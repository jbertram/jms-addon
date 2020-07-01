/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.jms.internal;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.google.inject.util.Providers;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.MessageListener;
import javax.jms.Session;
import org.seedstack.jms.spi.ConnectionDefinition;
import org.seedstack.jms.spi.JmsExceptionHandler;
import org.seedstack.jms.spi.JmsFactory;
import org.seedstack.jms.spi.MessageListenerDefinition;
import org.seedstack.jms.spi.MessageListenerInstanceDefinition;
import org.seedstack.jms.spi.MessagePoller;
import org.seedstack.seed.core.internal.transaction.TransactionalProxy;

class JmsModule extends AbstractModule {
    private final JmsFactory jmsFactory;
    private final Map<String, Connection> connections;
    private final Map<String, MessageListenerDefinition> messageListenerDefinitions;
    private final Map<String, ConnectionDefinition> connectionDefinitions;
    private final Collection<MessagePoller> pollers;

    public JmsModule(JmsFactory jmsFactory, ConcurrentMap<String, Connection> connections,
            ConcurrentMap<String, ConnectionDefinition> connectionDefinitions,
            Map<String, MessageListenerDefinition> messageListenerDefinitions, Collection<MessagePoller> pollers) {
        this.jmsFactory = jmsFactory;
        this.connections = connections;
        this.connectionDefinitions = connectionDefinitions;
        this.messageListenerDefinitions = messageListenerDefinitions;
        this.pollers = pollers;
    }

    @Override
    protected void configure() {
        requestStaticInjection(ExceptionListenerAdapter.class);
        requestStaticInjection(MessageListenerAdapter.class);

        bind(JmsFactory.class).toInstance(jmsFactory);
        requestInjection(jmsFactory);

        JmsSessionLink jmsSessionLink = new JmsSessionLink();
        bind(Session.class).toInstance(TransactionalProxy.create(Session.class, jmsSessionLink));

        jmsFactory.getConnectionFactories()
                .forEach((name, cf) -> bind(ConnectionFactory.class).annotatedWith(Names.named(name)).toInstance(cf));
        connections.forEach((key, value) -> bindConnection(connectionDefinitions.get(key), value, jmsSessionLink));
        messageListenerDefinitions.forEach((key, value) -> bindMessageListener(value));
        pollers.forEach(this::requestInjection);
    }

    private void bindMessageListener(MessageListenerDefinition messageListenerDefinition) {
        String name = messageListenerDefinition.getName();

        bind(JmsListenerTransactionHandler.class)
                .annotatedWith(Names.named(name))
                .toInstance(new JmsListenerTransactionHandler(messageListenerDefinition.getSession()));

        if (messageListenerDefinition instanceof MessageListenerInstanceDefinition) {
            MessageListener messageListener =
                    ((MessageListenerInstanceDefinition) messageListenerDefinition).getMessageListener();
            bind(MessageListener.class).annotatedWith(Names.named(name)).toInstance(messageListener);
        } else {
            bind(MessageListener.class).annotatedWith(Names.named(name))
                    .to(messageListenerDefinition.getMessageListenerClass());
        }
    }

    private void bindConnection(ConnectionDefinition connectionDefinition, Connection connection,
            JmsSessionLink jmsSessionLink) {
        String name = connectionDefinition.getName();

        Class<? extends JmsExceptionHandler> jmsExceptionHandlerClass =
                connectionDefinition.getJmsExceptionHandlerClass();
        if (jmsExceptionHandlerClass != null) {
            bind(JmsExceptionHandler.class).annotatedWith(Names.named(name)).to(jmsExceptionHandlerClass);
        } else {
            bind(JmsExceptionHandler.class).annotatedWith(Names.named(name)).toProvider(Providers.of(null));
        }

        if (connectionDefinition.getExceptionListenerClass() != null) {
            bind(ExceptionListener.class).annotatedWith(Names.named(name))
                    .to(connectionDefinition.getExceptionListenerClass());
        }

        bind(Connection.class).annotatedWith(Names.named(name)).toInstance(connection);

        JmsTransactionHandler transactionHandler = new JmsTransactionHandler(jmsSessionLink, connection);
        bind(JmsTransactionHandler.class).annotatedWith(Names.named(name)).toInstance(transactionHandler);
    }
}
