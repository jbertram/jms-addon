/*
 * Copyright © 2013-2020, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import org.seedstack.coffig.Config;
import org.seedstack.jms.spi.JmsExceptionHandler;
import org.seedstack.seed.validation.NotBlank;

@Config("jms")
public class JmsConfig {
    private boolean enabled = true;
    private Map<String, ConnectionFactoryConfig> connectionFactories = new HashMap<>();
    private Map<String, ConnectionConfig> connections = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public JmsConfig setEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    public Map<String, ConnectionFactoryConfig> getConnectionFactories() {
        return Collections.unmodifiableMap(connectionFactories);
    }

    public JmsConfig addConnectionFactory(String name, ConnectionFactoryConfig connectionFactoryConfig) {
        this.connectionFactories.put(name, connectionFactoryConfig);
        return this;
    }

    public Map<String, ConnectionConfig> getConnections() {
        return Collections.unmodifiableMap(connections);
    }

    public JmsConfig addConnection(String name, ConnectionConfig connectionConfig) {
        this.connections.put(name, connectionConfig);
        return this;
    }

    public static class ConnectionFactoryConfig {
        private static final String DEFAULT_JNDI_CONTEXT = "default";

        private String jndiName;
        private String jndiContext = DEFAULT_JNDI_CONTEXT;
        private Class<? extends ConnectionFactory> vendorClass;
        private Properties vendorProperties = new Properties();

        public String getJndiName() {
            return jndiName;
        }

        public ConnectionFactoryConfig setJndiName(String jndiName) {
            this.jndiName = jndiName;
            return this;
        }

        public String getJndiContext() {
            return jndiContext;
        }

        public ConnectionFactoryConfig setJndiContext(String jndiContext) {
            this.jndiContext = jndiContext;
            return this;
        }

        public Class<? extends ConnectionFactory> getVendorClass() {
            return vendorClass;
        }

        public ConnectionFactoryConfig setVendorClass(Class<? extends ConnectionFactory> vendorClass) {
            this.vendorClass = vendorClass;
            return this;
        }

        public Properties getVendorProperties() {
            return vendorProperties;
        }

        public ConnectionFactoryConfig setVendorProperties(Properties vendorProperties) {
            this.vendorProperties = vendorProperties;
            return this;
        }

        public ConnectionFactoryConfig setVendorProperty(String key, String value) {
            this.vendorProperties.setProperty(key, value);
            return this;
        }
    }

    public static class ConnectionConfig {
        private static final int DEFAULT_RECONNECTION_DELAY = 30000;

        @NotBlank
        private String connectionFactory;
        private Class<? extends ExceptionListener> exceptionListener;
        private Class<? extends JmsExceptionHandler> exceptionHandler;
        private String clientId;
        private String user;
        private String password;
        private boolean managed = true;
        private boolean jeeMode = false;
        private Boolean setClientId;
        private int reconnectionDelay = DEFAULT_RECONNECTION_DELAY;

        public String getConnectionFactory() {
            return connectionFactory;
        }

        public ConnectionConfig setConnectionFactory(String connectionFactory) {
            this.connectionFactory = connectionFactory;
            return this;
        }

        public Class<? extends ExceptionListener> getExceptionListener() {
            return exceptionListener;
        }

        public ConnectionConfig setExceptionListener(Class<? extends ExceptionListener> exceptionListener) {
            this.exceptionListener = exceptionListener;
            return this;
        }

        public Class<? extends JmsExceptionHandler> getExceptionHandler() {
            return exceptionHandler;
        }

        public ConnectionConfig setExceptionHandler(Class<? extends JmsExceptionHandler> exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        @SuppressFBWarnings(value = "NM_CONFUSING", justification = "Stupid check")
        public String getClientId() {
            return clientId;
        }

        public ConnectionConfig setClientId(String clientId) {
            this.clientId = clientId;
            return this;
        }

        public String getUser() {
            return user;
        }

        public ConnectionConfig setUser(String user) {
            this.user = user;
            return this;
        }

        public String getPassword() {
            return password;
        }

        public ConnectionConfig setPassword(String password) {
            this.password = password;
            return this;
        }

        public boolean isManaged() {
            return managed;
        }

        public ConnectionConfig setManaged(boolean managed) {
            this.managed = managed;
            return this;
        }

        public boolean isJeeMode() {
            return jeeMode;
        }

        public ConnectionConfig setJeeMode(boolean jeeMode) {
            this.jeeMode = jeeMode;
            return this;
        }

        public boolean isSetClientId() {
            if (setClientId == null) {
                return !jeeMode;
            } else {
                return setClientId;
            }
        }

        public ConnectionConfig setSetClientId(boolean setClientId) {
            this.setClientId = setClientId;
            return this;
        }

        public int getReconnectionDelay() {
            return reconnectionDelay;
        }

        public ConnectionConfig setReconnectionDelay(int reconnectionDelay) {
            this.reconnectionDelay = reconnectionDelay;
            return this;
        }
    }
}
