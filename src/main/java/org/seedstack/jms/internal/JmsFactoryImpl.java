/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;

import jodd.bean.BeanUtil;
import jodd.bean.BeanUtilBean;
import org.apache.commons.lang.StringUtils;
import org.seedstack.jms.JmsConfig;
import org.seedstack.jms.spi.ConnectionDefinition;
import org.seedstack.jms.spi.JmsFactory;
import org.seedstack.seed.SeedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.naming.Context;
import javax.naming.NamingException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Factory to create JMS objects.
 */
class JmsFactoryImpl implements JmsFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(JmsFactoryImpl.class);

    private final ConcurrentMap<String, ConnectionFactory> connectionFactoryMap = new ConcurrentHashMap<>();
    private final Map<String, Context> jndiContexts;
    private final String applicationName;
    private final JmsConfig jmsConfig;

    JmsFactoryImpl(String applicationName, JmsConfig jmsConfig, Map<String, Context> jndiContexts) {
        this.applicationName = applicationName;
        this.jmsConfig = jmsConfig;
        this.jndiContexts = jndiContexts;

        configureConnectionFactories();
    }

    @Override
    public Connection createConnection(ConnectionDefinition connectionDefinition) throws JMSException {
        Connection connection;

        if (connectionDefinition.isManaged()) {
            connection = new ManagedConnection(connectionDefinition, this);
            if (connectionDefinition.getExceptionListenerClass() != null) {
                LOGGER.debug("Setting exception listener {} on managed connection {}", connectionDefinition.getExceptionListenerClass(), connectionDefinition.getName());
                connection.setExceptionListener(new ExceptionListenerAdapter(connectionDefinition.getName()));
            }
        } else {
            connection = createRawConnection(connectionDefinition);
            if (!connectionDefinition.isJeeMode()) {
                if (connectionDefinition.getExceptionListenerClass() != null) {
                    LOGGER.debug("Setting exception listener {} on connection {}", connectionDefinition.getExceptionListenerClass(), connectionDefinition.getName());
                    connection.setExceptionListener(new ExceptionListenerAdapter(connectionDefinition.getName()));
                }
            }
        }

        return connection;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ConnectionDefinition createConnectionDefinition(String connectionName, JmsConfig.ConnectionConfig connectionConfig, ConnectionFactory connectionFactory) {
        // Find connection factory if not given explicitly
        if (connectionFactory == null) {
            connectionFactory = connectionFactoryMap.get(connectionConfig.getConnectionFactory());

            if (connectionFactory == null) {
                throw SeedException.createNew(JmsErrorCode.MISSING_CONNECTION_FACTORY).put("connectionName", connectionName);
            }
        }

        boolean jeeMode = connectionConfig.isJeeMode();
        boolean shouldSetClientId = connectionConfig.isSetClientId();

        if (jeeMode && shouldSetClientId) {
            throw SeedException.createNew(JmsErrorCode.CANNOT_SET_CLIENT_ID_IN_JEE_MODE).put(JmsPlugin.ERROR_CONNECTION_NAME, connectionName);
        }

        return new ConnectionDefinition(
                connectionName,
                connectionFactory,
                connectionConfig.isManaged(),
                jeeMode,
                shouldSetClientId,
                Optional.ofNullable(connectionConfig.getClientId()).orElse(applicationName + "-" + connectionName),
                connectionConfig.getUser(),
                connectionConfig.getPassword(),
                connectionConfig.getReconnectionDelay(),
                connectionConfig.getExceptionListener(),
                connectionConfig.getExceptionHandler()
        );
    }

    Connection createRawConnection(ConnectionDefinition connectionDefinition) throws JMSException {
        Connection connection;
        if (connectionDefinition.getUser() != null) {
            connection = connectionDefinition.getConnectionFactory().createConnection(connectionDefinition.getUser(), connectionDefinition.getPassword());
        } else {
            connection = connectionDefinition.getConnectionFactory().createConnection();
        }

        // client id is set here on raw connection
        if (connectionDefinition.isShouldSetClientId()) {
            LOGGER.debug("Setting client id as {} on connection {}", connectionDefinition.getClientId(), connectionDefinition.getName());
            connection.setClientID(connectionDefinition.getClientId());
        }

        return connection;
    }

    private void configureConnectionFactories() {
        for (Map.Entry<String, JmsConfig.ConnectionFactoryConfig> entry : jmsConfig.getConnectionFactories().entrySet()) {
            String connectionFactoryName = entry.getKey();
            JmsConfig.ConnectionFactoryConfig connectionFactoryConfig = entry.getValue();

            String jndiName = connectionFactoryConfig.getJndiName();
            String jndiContext = connectionFactoryConfig.getJndiContext();
            Class<? extends ConnectionFactory> connectionFactoryClass = connectionFactoryConfig.getVendorClass();

            Object connectionFactory;
            if (StringUtils.isNotBlank(jndiName)) {
                connectionFactory = lookupConnectionFactory(connectionFactoryName, jndiContext, jndiName);
            } else if (connectionFactoryClass != null) {
                try {
                    connectionFactory = connectionFactoryClass.newInstance();
                    setProperties(connectionFactory, connectionFactoryConfig.getVendorProperties());
                } catch (Exception e) {
                    throw SeedException.wrap(e, JmsErrorCode.UNABLE_TO_CREATE_CONNECTION_FACTORY).put("connectionFactoryName", connectionFactoryName);
                }
            } else {
                throw SeedException.createNew(JmsErrorCode.MISCONFIGURED_CONNECTION_FACTORY).put("connectionFactoryName", connectionFactoryName);
            }

            if (!(connectionFactory instanceof ConnectionFactory)) {
                throw SeedException.createNew(JmsErrorCode.UNRECOGNIZED_CONNECTION_FACTORY).put("classname", connectionFactoryClass);
            }

            connectionFactoryMap.put(connectionFactoryName, (ConnectionFactory) connectionFactory);
        }
    }

    private Object lookupConnectionFactory(String connectionFactoryName, String contextName, String jndiName) {
        try {
            if (this.jndiContexts == null || this.jndiContexts.isEmpty()) {
                throw SeedException.createNew(JmsErrorCode.NO_JNDI_CONTEXT).put("connectionFactoryName", connectionFactoryName);
            }

            Context context = this.jndiContexts.get(contextName);
            if (context == null) {
                throw SeedException.createNew(JmsErrorCode.MISSING_JNDI_CONTEXT).put("contextName", contextName).put("connectionFactoryName", connectionFactoryName);
            }

            return context.lookup(jndiName);
        } catch (NamingException e) {
            throw SeedException.wrap(e, JmsErrorCode.JNDI_LOOKUP_ERROR).put("connectionFactoryName", connectionFactoryName);
        }
    }

    private void setProperties(Object bean, Properties properties) {
        BeanUtil beanUtil = new BeanUtilBean();
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            try {
                if (!beanUtil.hasProperty(bean, key)) {
                    throw SeedException.createNew(JmsErrorCode.PROPERTY_NOT_FOUND).put("property", key).put("class", bean.getClass().getCanonicalName());
                }

                beanUtil.setProperty(bean, key, value);
            } catch (Exception e) {
                throw SeedException.wrap(e, JmsErrorCode.UNABLE_TO_SET_PROPERTY).put("property", key).put("class", bean.getClass().getCanonicalName()).put("value", value);
            }
        }
    }
}
