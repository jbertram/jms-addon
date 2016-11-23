/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;

import com.google.common.collect.Lists;
import io.nuun.kernel.api.plugin.InitState;
import io.nuun.kernel.api.plugin.context.InitContext;
import io.nuun.kernel.api.plugin.request.ClasspathScanRequest;
import org.apache.commons.lang.StringUtils;
import org.kametic.specifications.Specification;
import org.seedstack.jms.DestinationType;
import org.seedstack.jms.JmsConfig;
import org.seedstack.jms.JmsMessageListener;
import org.seedstack.jms.spi.ConnectionDefinition;
import org.seedstack.jms.spi.JmsExceptionHandler;
import org.seedstack.jms.spi.JmsFactory;
import org.seedstack.jms.spi.MessageListenerDefinition;
import org.seedstack.jms.spi.MessagePoller;
import org.seedstack.seed.Application;
import org.seedstack.seed.SeedException;
import org.seedstack.seed.core.internal.AbstractSeedPlugin;
import org.seedstack.seed.core.internal.jndi.JndiPlugin;
import org.seedstack.seed.core.internal.transaction.TransactionPlugin;
import org.seedstack.seed.core.utils.SeedCheckUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.naming.Context;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This plugin provides JMS support through JNDI or plain configuration.
 */
public class JmsPlugin extends AbstractSeedPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(JmsPlugin.class);

    static final String ERROR_CONNECTION_NAME = "connectionName";
    private static final String ERROR_MESSAGE_LISTENER_NAME = "messageListenerName";
    private static final String ERROR_DESTINATION_TYPE = "destinationType";

    @SuppressWarnings("unchecked")
    private final Specification<Class<?>> messageListenerSpec = and(classImplements(MessageListener.class), classAnnotatedWith(JmsMessageListener.class));
    private final Specification<Class<?>> exceptionListenerSpec = classImplements(ExceptionListener.class);
    private final Specification<Class<?>> exceptionHandlerSpec = classImplements(JmsExceptionHandler.class);

    private final ConcurrentMap<String, MessageListenerDefinition> messageListenerDefinitions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConnectionDefinition> connectionDefinitions = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, Connection> connections = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, MessagePoller> pollers = new ConcurrentHashMap<>();

    private final AtomicBoolean shouldStartConnections = new AtomicBoolean(false);

    private JmsFactory jmsFactory;
    private Application application;
    private TransactionPlugin transactionPlugin;

    @Override
    public Collection<Class<?>> dependencies() {
        return Lists.newArrayList(JndiPlugin.class, TransactionPlugin.class);
    }

    @Override
    public String name() {
        return "jms";
    }

    @Override
    public InitState initialize(InitContext initContext) {
        transactionPlugin = initContext.dependency(TransactionPlugin.class);
        application = getApplication();
        JmsConfig jmsConfig = getConfiguration(JmsConfig.class);
        Map<String, Context> jndiContexts = initContext.dependency(JndiPlugin.class).getJndiContexts();

        jmsFactory = new JmsFactoryImpl(getApplication().getId(), jmsConfig, jndiContexts);

        configureConnections(jmsConfig);

        configureMessageListeners(initContext.scannedTypesBySpecification().get(messageListenerSpec));

        return InitState.INITIALIZED;
    }

    @Override
    public void start(io.nuun.kernel.api.plugin.context.Context context) {
        shouldStartConnections.set(true);

        for (Map.Entry<String, Connection> connection : this.connections.entrySet()) {
            try {
                connection.getValue().start();
            } catch (JMSException e) {
                throw SeedException.wrap(e, JmsErrorCode.UNABLE_TO_START_JMS_CONNECTION).put(ERROR_CONNECTION_NAME, connection.getKey());
            }
        }

        pollers.values().forEach(MessagePoller::start);
    }

    @Override
    public void stop() {
        shouldStartConnections.set(false);

        pollers.values().forEach(MessagePoller::stop);

        for (Map.Entry<String, Connection> connection : this.connections.entrySet()) {
            try {
                connection.getValue().close();
            } catch (JMSException e) {
                LOGGER.error("Unable to cleanly stop JMS connection " + connection.getKey(), e);
            }
        }
    }

    @Override
    public Collection<ClasspathScanRequest> classpathScanRequests() {
        return classpathScanRequestBuilder()
                .specification(messageListenerSpec)
                .specification(exceptionListenerSpec)
                .specification(exceptionHandlerSpec)
                .build();
    }

    @Override
    public Object nativeUnitModule() {
        return new JmsModule(
                jmsFactory,
                connections,
                connectionDefinitions,
                messageListenerDefinitions,
                pollers.values()
        );
    }

    private void configureConnections(JmsConfig jmsConfig) {
        for (Map.Entry<String, JmsConfig.ConnectionConfig> entry : jmsConfig.getConnections().entrySet()) {
            try {
                ConnectionDefinition connectionDefinition = jmsFactory.createConnectionDefinition(entry.getKey(), entry.getValue(), null);
                registerConnection(jmsFactory.createConnection(connectionDefinition), connectionDefinition);
            } catch (JMSException e) {
                throw SeedException.wrap(e, JmsErrorCode.UNABLE_TO_CREATE_JMS_CONNECTION).put(ERROR_CONNECTION_NAME, entry.getKey());
            }
        }
    }

    private void configureMessageListeners(Collection<Class<?>> listenerCandidates) {
        for (Class<?> candidate : listenerCandidates) {
            if (MessageListener.class.isAssignableFrom(candidate)) {
                //noinspection unchecked
                Class<? extends MessageListener> messageListenerClass = (Class<? extends MessageListener>) candidate;
                String messageListenerName = messageListenerClass.getCanonicalName();
                JmsMessageListener annotation = messageListenerClass.getAnnotation(JmsMessageListener.class);

                boolean isTransactional;
                try {
                    isTransactional = transactionPlugin.isTransactional(messageListenerClass.getMethod("onMessage", Message.class));
                } catch (NoSuchMethodException e) {
                    throw SeedException.wrap(e, JmsErrorCode.INVALID_MESSAGE_LISTENER_CLASS)
                            .put("messageListenerClass", messageListenerClass.getName());
                }

                Connection listenerConnection = connections.get(annotation.connection());

                if (listenerConnection == null) {
                    throw SeedException.createNew(JmsErrorCode.MISSING_CONNECTION_FACTORY)
                            .put(ERROR_CONNECTION_NAME, annotation.connection())
                            .put(ERROR_MESSAGE_LISTENER_NAME, messageListenerName);
                }

                Session session;
                try {
                    session = listenerConnection.createSession(isTransactional, Session.AUTO_ACKNOWLEDGE);
                } catch (JMSException e) {
                    throw SeedException.wrap(e, JmsErrorCode.UNABLE_TO_CREATE_SESSION)
                            .put(ERROR_CONNECTION_NAME, annotation.connection())
                            .put(ERROR_MESSAGE_LISTENER_NAME, messageListenerName);
                }

                Destination destination;
                DestinationType destinationType;

                if (!annotation.destinationTypeStr().isEmpty()) {
                    try {
                        destinationType = DestinationType.valueOf(application.substituteWithConfiguration(annotation.destinationTypeStr()));
                    } catch (IllegalArgumentException e) {
                        throw SeedException.wrap(e, JmsErrorCode.UNKNOWN_DESTINATION_TYPE)
                                .put(ERROR_DESTINATION_TYPE, annotation.destinationTypeStr())
                                .put(ERROR_CONNECTION_NAME, annotation.connection())
                                .put(ERROR_MESSAGE_LISTENER_NAME, messageListenerName);
                    }
                } else {
                    destinationType = annotation.destinationType();
                }
                try {
                    switch (destinationType) {
                        case QUEUE:
                            destination = session.createQueue(application.substituteWithConfiguration(annotation.destinationName()));
                            break;
                        case TOPIC:
                            destination = session.createTopic(application.substituteWithConfiguration(annotation.destinationName()));
                            break;
                        default:
                            throw SeedException.createNew(JmsErrorCode.UNKNOWN_DESTINATION_TYPE)
                                    .put(ERROR_DESTINATION_TYPE, destinationType)
                                    .put(ERROR_CONNECTION_NAME, annotation.connection())
                                    .put(ERROR_MESSAGE_LISTENER_NAME, messageListenerName);
                    }
                } catch (JMSException e) {
                    throw SeedException.wrap(e, JmsErrorCode.UNABLE_TO_CREATE_DESTINATION)
                            .put(ERROR_DESTINATION_TYPE, destinationType.name())
                            .put(ERROR_CONNECTION_NAME, annotation.connection())
                            .put(ERROR_MESSAGE_LISTENER_NAME, messageListenerName);
                }

                Class<? extends MessagePoller> messagePollerClass = null;
                if (annotation.poller().length > 0) {
                    messagePollerClass = annotation.poller()[0];
                }

                registerMessageListener(
                        new MessageListenerDefinition(
                                messageListenerName,
                                application.substituteWithConfiguration(annotation.connection()),
                                session,
                                destination,
                                application.substituteWithConfiguration(annotation.selector()),
                                messageListenerClass,
                                messagePollerClass
                        )
                );
            }
        }
    }

    private MessageConsumer createMessageConsumer(MessageListenerDefinition messageListenerDefinition) throws JMSException {
        LOGGER.debug("Creating JMS consumer for listener {}", messageListenerDefinition.getName());

        MessageConsumer consumer;
        Session session = messageListenerDefinition.getSession();

        if (StringUtils.isNotBlank(messageListenerDefinition.getSelector())) {
            consumer = session.createConsumer(messageListenerDefinition.getDestination(), messageListenerDefinition.getSelector());
        } else {
            consumer = session.createConsumer(messageListenerDefinition.getDestination());
        }

        MessagePoller messagePoller;
        if (messageListenerDefinition.getPoller() != null) {
            try {
                LOGGER.debug("Creating poller for JMS listener {}", messageListenerDefinition.getName());

                Connection connection = connections.get(messageListenerDefinition.getConnectionName());

                messagePoller = messageListenerDefinition.getPoller().newInstance();
                messagePoller.setSession(session);
                messagePoller.setMessageConsumer(consumer);
                messagePoller.setMessageListener(new MessageListenerAdapter(messageListenerDefinition.getName()));

                if (connection instanceof ManagedConnection) {
                    messagePoller.setExceptionListener((ExceptionListener) connection);
                } else {
                    messagePoller.setExceptionListener(connection.getExceptionListener());
                }
            } catch (Exception e) {
                throw SeedException.wrap(e, JmsErrorCode.UNABLE_TO_CREATE_POLLER).put("pollerClass", messageListenerDefinition.getPoller());
            }

            pollers.put(messageListenerDefinition.getName(), messagePoller);
        } else {
            consumer.setMessageListener(new MessageListenerAdapter(messageListenerDefinition.getName()));
        }

        return consumer;
    }

    /**
     * Register an existing JMS connection to be managed by the JMS plugin.
     *
     * @param connection           the connection.
     * @param connectionDefinition the connection definition.
     */
    public void registerConnection(Connection connection, ConnectionDefinition connectionDefinition) {
        SeedCheckUtils.checkIfNotNull(connection);
        SeedCheckUtils.checkIfNotNull(connectionDefinition);

        if (this.connectionDefinitions.putIfAbsent(connectionDefinition.getName(), connectionDefinition) != null) {
            throw SeedException.createNew(JmsErrorCode.DUPLICATE_CONNECTION_NAME).put(ERROR_CONNECTION_NAME, connectionDefinition.getName());
        }

        if (this.connections.putIfAbsent(connectionDefinition.getName(), connection) != null) {
            throw SeedException.createNew(JmsErrorCode.DUPLICATE_CONNECTION_NAME).put(ERROR_CONNECTION_NAME, connectionDefinition.getName());
        }

        if (shouldStartConnections.get()) {
            try {
                connection.start();
            } catch (JMSException e) {
                throw SeedException.wrap(e, JmsErrorCode.UNABLE_TO_START_JMS_CONNECTION).put(ERROR_CONNECTION_NAME, connectionDefinition.getName());
            }
        }
    }

    /**
     * Register a message listener definition to be managed by the JMS plugin.
     *
     * @param messageListenerDefinition the message listener definition.
     */
    public void registerMessageListener(MessageListenerDefinition messageListenerDefinition) {
        SeedCheckUtils.checkIfNotNull(messageListenerDefinition);

        ConnectionDefinition connectionDefinition = connectionDefinitions.get(messageListenerDefinition.getConnectionName());
        if (connectionDefinition.isJeeMode() && messageListenerDefinition.getPoller() == null) {
            throw SeedException.createNew(JmsErrorCode.MESSAGE_POLLER_REQUIRED_IN_JEE_MODE)
                    .put(ERROR_CONNECTION_NAME, connectionDefinition.getName())
                    .put(ERROR_MESSAGE_LISTENER_NAME, messageListenerDefinition.getName());
        }

        try {
            createMessageConsumer(messageListenerDefinition);
        } catch (JMSException e) {
            throw SeedException.wrap(e, JmsErrorCode.UNABLE_TO_CREATE_MESSAGE_CONSUMER)
                    .put(ERROR_MESSAGE_LISTENER_NAME, messageListenerDefinition.getName());
        }

        if (messageListenerDefinitions.putIfAbsent(messageListenerDefinition.getName(), messageListenerDefinition) != null) {
            throw SeedException.createNew(JmsErrorCode.DUPLICATE_MESSAGE_LISTENER_NAME)
                    .put(ERROR_MESSAGE_LISTENER_NAME, messageListenerDefinition.getName());
        }
    }

    /**
     * Retrieve a connection by name.
     *
     * @param name the name of the connection to retrieve.
     * @return the connection or null if it doesn't exists.
     */
    public Connection getConnection(String name) {
        return connections.get(name);
    }

    /**
     * Return the factory used to create JMS objects.
     *
     * @return the JMS factory.
     */
    public JmsFactory getJmsFactory() {
        return jmsFactory;
    }
}
