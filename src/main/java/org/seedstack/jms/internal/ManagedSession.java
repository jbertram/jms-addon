/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This session is a facade of a jms session. It allows the reconnection mechanism.
 */
class ManagedSession implements Session {

    private static final Logger logger = LoggerFactory.getLogger(ManagedSession.class);

    private final Boolean transacted;

    private final Integer acknowledgeMode;

    private Session session;
    
    private final boolean polling;

    private List<ManagedMessageConsumer> managedMessageConsumers = new ArrayList<>();

    private ReentrantReadWriteLock sessionLock = new ReentrantReadWriteLock();

    ManagedSession(Session session, boolean transacted, int acknowledgeMode, boolean polling) {
        checkNotNull(session);
        
        this.session = session;
        this.transacted = transacted;
        this.acknowledgeMode = acknowledgeMode;
        this.polling = polling;
    }

    void refresh(Connection connection) throws JMSException {
        sessionLock.writeLock().lock();
        try {
            session = connection.createSession(this.transacted, this.acknowledgeMode);

            for (ManagedMessageConsumer managedMessageConsumer : managedMessageConsumers) {
                managedMessageConsumer.refresh(session);
            }
        } finally {
            sessionLock.writeLock().unlock();
        }
    }

    /**
     * Reset the session and the message consumers on cascade.
     */
    void reset() {
        sessionLock.writeLock().lock();
        try {
            logger.trace("Resetting session");
            session = null;
            for (ManagedMessageConsumer managedMessageConsumer : managedMessageConsumers) {
                managedMessageConsumer.reset();
            }
        } finally {
            sessionLock.writeLock().unlock();
        }
    }

    private Session getSession() throws JMSException {
        sessionLock.readLock().lock();
        try {
            if (session == null) {
                throw new JMSException("Attempt to use a session during connection refresh");
            }

            return session;
        } finally {
            sessionLock.readLock().unlock();
        }
    }

    @Override
    public BytesMessage createBytesMessage() throws JMSException {
        return getSession().createBytesMessage();
    }

    @Override
    public MapMessage createMapMessage() throws JMSException {
        return getSession().createMapMessage();
    }

    @Override
    public Message createMessage() throws JMSException {
        return getSession().createMessage();
    }

    @Override
    public ObjectMessage createObjectMessage() throws JMSException {
        return getSession().createObjectMessage();
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable object) throws JMSException {
        return getSession().createObjectMessage(object);
    }

    @Override
    public StreamMessage createStreamMessage() throws JMSException {
        return getSession().createStreamMessage();
    }

    @Override
    public TextMessage createTextMessage() throws JMSException {
        return getSession().createTextMessage();
    }

    @Override
    public TextMessage createTextMessage(String text) throws JMSException {
        return getSession().createTextMessage(text);
    }

    @Override
    public boolean getTransacted() throws JMSException {
        return getSession().getTransacted();
    }

    @Override
    public int getAcknowledgeMode() throws JMSException {
        return getSession().getAcknowledgeMode();
    }

    @Override
    public void commit() throws JMSException {
        getSession().commit();
    }

    @Override
    public void rollback() throws JMSException {
        getSession().rollback();
    }

    @Override
    public void close() throws JMSException {
        getSession().close();
    }

    @Override
    public void recover() throws JMSException {
        getSession().recover();
    }

    @Override
    public MessageListener getMessageListener() throws JMSException {
        return getSession().getMessageListener();
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException {
        getSession().setMessageListener(listener);
    }

    @Override
    public void run() {
        sessionLock.readLock().lock();
        try {
            if (session == null) {
                throw new IllegalStateException("The connection is closed");
            }
            session.run();
        } finally {
            sessionLock.readLock().unlock();
        }
    }

    @Override
    public MessageProducer createProducer(Destination destination) throws JMSException {
        return getSession().createProducer(destination);
    }

    @Override
    public MessageConsumer createConsumer(Destination destination) throws JMSException {
        return createConsumer(destination, null, false);
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector) throws JMSException {
        return createConsumer(destination, messageSelector, false);
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean NoLocal) throws JMSException {
        ManagedMessageConsumer consumer = new ManagedMessageConsumer(getSession().createConsumer(destination, messageSelector, NoLocal), destination, messageSelector, NoLocal, polling);
        managedMessageConsumers.add(consumer);
        return consumer;
    }

    @Override
    public Queue createQueue(String queueName) throws JMSException {
        return getSession().createQueue(queueName);
    }

    @Override
    public Topic createTopic(String topicName) throws JMSException {
        return getSession().createTopic(topicName);
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException {
        return getSession().createDurableSubscriber(topic, name);
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException {
        return getSession().createDurableSubscriber(topic, name, messageSelector, noLocal);
    }

    @Override
    public QueueBrowser createBrowser(Queue queue) throws JMSException {
        return getSession().createBrowser(queue);
    }

    @Override
    public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException {
        return getSession().createBrowser(queue, messageSelector);
    }

    @Override
    public TemporaryQueue createTemporaryQueue() throws JMSException {
        return getSession().createTemporaryQueue();
    }

    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException {
        return getSession().createTemporaryTopic();
    }

    @Override
    public void unsubscribe(String name) throws JMSException {
        getSession().unsubscribe(name);
    }
}
