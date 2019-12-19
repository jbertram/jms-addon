/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.jms.pollers;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import org.seedstack.jms.spi.MessagePoller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link MessagePoller} implementing a simple polling strategy that waits at most 30 seconds using
 * {@link MessageConsumer#receive(long)} and then loop over. When a message is received, the {@link MessageListener} is
 * invoked and the session is committed. If an exception occurs the session is rollbacked, the {@link ExceptionListener}
 * is invoked if any, the polling thread is shutdown and scheduled for restart in 10 seconds.
 */
public class SimpleMessagePoller implements MessagePoller, Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleMessagePoller.class);
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final Timer timer = new Timer();
    private long receiveTimeout = 30000;
    private int restartDelay = 10000;
    private Thread thread;
    private Session session;
    private ExceptionListener exceptionListener;
    private MessageListener messageListener;
    private MessageConsumer messageConsumer;

    @Override
    public void setSession(Session session) {
        this.session = session;
    }

    @Override
    public void setMessageConsumer(MessageConsumer messageConsumer) {
        this.messageConsumer = messageConsumer;
    }

    @Override
    public void setExceptionListener(ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
    }

    @Override
    public void setMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }

    @Override
    public synchronized void start() {
        if (!active.getAndSet(true)) {
            checkNotNull(this.session);
            checkNotNull(this.messageConsumer);
            checkNotNull(this.messageListener);

            startThread();
        }
    }

    @Override
    public synchronized void stop() {
        if (active.getAndSet(false)) {
            timer.cancel();
            thread.interrupt();
        }
    }

    @Override
    public void run() {
        LOGGER.debug("Starting to poll messages for JMS listener {}", messageListener);

        while (active.get()) {
            try {
                Message message = messageConsumer.receive(receiveTimeout);
                if (message != null) {
                    messageListener.onMessage(message);
                    session.commit();
                }
            } catch (Exception e) {
                try {
                    session.rollback();
                } catch (JMSException rollbackException) {
                    LOGGER.warn("Unable to rollback after message listener exception", rollbackException);
                }

                if (exceptionListener != null && e instanceof JMSException) {
                    exceptionListener.onException((JMSException) e);
                } else {
                    LOGGER.error("An exception occurred during JMS polling and no exception listener was defined", e);
                }

                break;
            }
        }

        if (active.get()) {
            LOGGER.warn("Message polling interrupted for JMS listener {}. Scheduling restart in {} ms",
                    messageListener,
                    restartDelay);

            try {
                timer.schedule(new MyTimerTask(), restartDelay);
            } catch (Exception e) {
                LOGGER.error(
                        "Unable to schedule polling restart for JMS listener {}, consider restarting the poller " +
                                "manually if possible",
                        messageListener);
            }
        } else {
            LOGGER.debug("Stopping to poll messages for JMS listener {}", messageListener, restartDelay);
        }
    }

    private void startThread() {
        thread = new Thread(this);
        thread.setName("jms-poller-" + thread.getId());
        thread.start();
    }

    private class MyTimerTask extends TimerTask {
        @Override
        public void run() {
            synchronized (SimpleMessagePoller.this) {
                if (!thread.isAlive()) {
                    startThread();
                } else {
                    timer.schedule(new MyTimerTask(), restartDelay);
                }
            }
        }
    }
}
