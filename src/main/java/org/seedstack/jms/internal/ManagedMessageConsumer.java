/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;

import org.seedstack.seed.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This session is a facade of a jms messageConsumer. It allows the reconnection mechanism.
 */
class ManagedMessageConsumer implements MessageConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedMessageConsumer.class);
    private final Destination destination;
    private final String messageSelector;
    private final boolean noLocal;
    private final boolean polling;
    private final ReentrantReadWriteLock messageConsumerLock = new ReentrantReadWriteLock();
    private final ManagedSession managedSession;
    private MessageListener messageListener;
    private MessageConsumer messageConsumer;

    ManagedMessageConsumer(MessageConsumer messageConsumer, Destination destination, @Nullable String messageSelector, boolean noLocal, boolean polling, ManagedSession managedSession) {
        checkNotNull(messageConsumer);
        checkNotNull(destination);

        LOGGER.debug("Creating managed JMS message consumer {}", this);

        this.messageConsumer = messageConsumer;
        this.messageSelector = messageSelector;
        this.destination = destination;
        this.noLocal = noLocal;
        this.polling = polling;
        this.managedSession = managedSession;
    }

    void refresh(Session session) throws JMSException {
        messageConsumerLock.writeLock().lock();
        try {
            // Create a new messageConsumer
            LOGGER.debug("Refreshing managed JMS message consumer {}", this);
            if (this.noLocal) {
                messageConsumer = session.createConsumer(destination, messageSelector, true);
            } else if (messageSelector != null && !"".equals(messageSelector)) {
                messageConsumer = session.createConsumer(destination, messageSelector);
            } else {
                messageConsumer = session.createConsumer(destination);
            }

            // Refresh the message listener if it exists
            if (messageListener != null && !this.polling) {
                messageConsumer.setMessageListener(messageListener);
            }
        } finally {
            messageConsumerLock.writeLock().unlock();
        }
    }

    void reset() {
        messageConsumerLock.writeLock().lock();
        try {
            LOGGER.debug("Resetting managed JMS message consumer {}", this);
            messageConsumer = null;
        } finally {
            messageConsumerLock.writeLock().unlock();
        }
    }

    private MessageConsumer getMessageConsumer() throws JMSException {
        messageConsumerLock.readLock().lock();
        try {
            if (messageConsumer == null) {
                throw new JMSException("Attempt to use a message consumer during connection refresh");
            }
            return messageConsumer;
        } finally {
            messageConsumerLock.readLock().unlock();
        }
    }

    @Override
    public String getMessageSelector() throws JMSException {
        return getMessageConsumer().getMessageSelector();
    }

    @Override
    public Message receiveNoWait() throws JMSException {
        return getMessageConsumer().receiveNoWait();
    }

    @Override
    public MessageListener getMessageListener() throws JMSException {
        return getMessageConsumer().getMessageListener();
    }

    @Override
    public void close() throws JMSException {
        try {
            LOGGER.debug("Closing managed JMS message consumer {}", this);
            getMessageConsumer().close();
        } finally {
            managedSession.removeMessageConsumer(this);
        }
    }

    @Override
    public Message receive(long timeout) throws JMSException {
        return getMessageConsumer().receive(timeout);
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException {
        if (!polling) {
            getMessageConsumer().setMessageListener(listener);
        }
        messageListener = listener;
    }

    @Override
    public Message receive() throws JMSException {
        return getMessageConsumer().receive();
    }
}
