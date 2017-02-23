/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;

import com.google.common.collect.Sets;
import org.seedstack.jms.spi.ConnectionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import java.util.Calendar;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * This connection is a facade to the actual jms connection. It provides the reconnection mechanism.
 */
class ManagedConnection implements Connection, ExceptionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(ManagedConnection.class);
    private final Set<ManagedSession> sessions = Sets.newConcurrentHashSet();
    private final AtomicBoolean needToStart = new AtomicBoolean(false);
    private final ConnectionDefinition connectionDefinition;
    private final JmsFactoryImpl jmsFactoryImpl;
    private final AtomicBoolean scheduleInProgress;
    private final ReentrantReadWriteLock connectionLock = new ReentrantReadWriteLock();
    private Connection connection;
    private ExceptionListener exceptionListener;

    ManagedConnection(ConnectionDefinition connectionDefinition, JmsFactoryImpl jmsFactoryImpl) throws JMSException {
        checkNotNull(connectionDefinition);

        this.jmsFactoryImpl = jmsFactoryImpl;
        this.connectionDefinition = connectionDefinition;
        this.scheduleInProgress = new AtomicBoolean(false);
        this.connection = createConnection();
    }

    private Connection createConnection() throws JMSException {
        LOGGER.debug("Initializing managed JMS connection {}", connectionDefinition.getName());

        Connection newConnection = jmsFactoryImpl.createRawConnection(connectionDefinition);

        // Set the exception listener to ourselves so we can monitor the underlying connection
        if (!connectionDefinition.isJeeMode()) {
            newConnection.setExceptionListener(this);
        }

        return newConnection;
    }

    private void scheduleReconnection() {
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                connectionLock.writeLock().lock();

                try {
                    // Recreate the connection
                    LOGGER.info("Recreating managed JMS connection {}", connectionDefinition.getName());
                    connection = createConnection();

                    // Refresh sessions
                    for (ManagedSession session : sessions) {
                        session.refresh(connection);
                    }

                    // Start the new connection if needed
                    if (needToStart.get()) {
                        LOGGER.info("Restarting managed JMS connection {}", connectionDefinition.getName());
                        connection.start();
                        scheduleInProgress.set(false);
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to restart managed JMS connection {}, next attempt in {} ms", connectionDefinition.getName(), connectionDefinition.getReconnectionDelay());
                    scheduleReconnection();
                } finally {
                    connectionLock.writeLock().unlock();
                }

            }
        };

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MILLISECOND, connectionDefinition.getReconnectionDelay());
        new Timer().schedule(timerTask, calendar.getTime());
    }

    private Connection getConnection() throws JMSException {
        connectionLock.readLock().lock();
        try {
            if (connection == null) {
                throw new JMSException("Managed JMS connection " + connectionDefinition.getName() + " is not ready");
            }

            return connection;
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    @Override
    public void onException(JMSException exception) {
        LOGGER.error("An exception occurred on managed JMS connection {}", connectionDefinition.getName());
        if (exception != null && LOGGER.isDebugEnabled()) {
            LOGGER.debug("Original exception below", exception);
        }

        if (exceptionListener != null) {
            exceptionListener.onException(exception);
        }

        reset();
    }

    private void reset() {
        if (scheduleInProgress.getAndSet(true)) {
            LOGGER.debug("Managed JMS connection {} already scheduled for restart", connectionDefinition.getName());
        } else {
            // reset the connection
            LOGGER.warn("Resetting managed JMS connection {} and scheduling restart in {} ms", connectionDefinition.getName(), connectionDefinition.getReconnectionDelay());

            connectionLock.writeLock().lock();
            try {
                // Reset the sessions to prevent their use during refresh
                for (ManagedSession session : sessions) {
                    session.reset();
                }

                // Effectively close th connection and prevent its use during refresh
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (JMSException e) {
                        LOGGER.warn("Unable to cleanly close the managed JMS connection {}", connectionDefinition.getName());
                    }
                }
                connection = null;

                // Schedule
                scheduleReconnection();
            } finally {
                connectionLock.writeLock().unlock();
            }
        }
    }

    // Delegated methods

    @Override
    public Session createSession(boolean transacted, int acknowledgeMode) throws JMSException {
        connectionLock.readLock().lock();
        try {
            ManagedSession managedSession = new ManagedSession(
                    getConnection().createSession(transacted, acknowledgeMode),
                    transacted,
                    acknowledgeMode,
                    connectionDefinition.isJeeMode(),
                    this);
            sessions.add(managedSession);
            return managedSession;
        } finally {
            connectionLock.readLock().unlock();
        }
    }

    @Override
    public ConnectionConsumer createConnectionConsumer(Destination destination, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        return getConnection().createConnectionConsumer(destination, messageSelector, sessionPool, maxMessages);
    }

    @Override
    public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        return getConnection().createDurableConnectionConsumer(topic, subscriptionName, messageSelector, sessionPool, maxMessages);
    }

    @Override
    public void close() throws JMSException {
        LOGGER.info("Closing managed JMS connection {}", connectionDefinition.getName());
        getConnection().close();
    }

    @Override
    public void start() throws JMSException {
        LOGGER.info("Starting managed JMS connection {}", connectionDefinition.getName());
        getConnection().start();
        needToStart.set(true);
    }

    @Override
    public void stop() throws JMSException {
        LOGGER.info("Stopping managed JMS connection {}", connectionDefinition.getName());
        getConnection().stop();
        needToStart.set(false);
    }

    @Override
    public ConnectionMetaData getMetaData() throws JMSException {
        return getConnection().getMetaData();
    }

    @Override
    public void setExceptionListener(ExceptionListener exceptionListener) throws JMSException {
        this.exceptionListener = exceptionListener;
    }

    @Override
    public ExceptionListener getExceptionListener() throws JMSException {
        return exceptionListener;
    }

    @Override
    public void setClientID(String clientID) throws JMSException {
        throw new IllegalStateException("Client ID cannot be changed on managed connections");
    }

    @Override
    public String getClientID() throws JMSException {
        throw new IllegalStateException("Client ID cannot be retrieved on managed connections");
    }

    void removeSession(ManagedSession managedSession) {
        sessions.remove(managedSession);
    }
}
