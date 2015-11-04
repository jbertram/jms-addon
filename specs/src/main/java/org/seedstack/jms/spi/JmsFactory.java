/**
 * Copyright (c) 2013-2015, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.spi;

import org.apache.commons.configuration.Configuration;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

public interface JmsFactory {
    Connection createConnection(ConnectionDefinition connectionDefinition) throws JMSException;

    ConnectionDefinition createConnectionDefinition(String connectionName, Configuration configuration, ConnectionFactory connectionFactory);
}
