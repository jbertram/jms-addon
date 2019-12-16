/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.fixtures;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import org.seedstack.jms.DestinationType;
import org.seedstack.jms.JMSRollbackException;
import org.seedstack.jms.JmsMessageListener;
import org.seedstack.seed.Logging;
import org.slf4j.Logger;

@JmsMessageListener(connection = "connection3", destinationType = DestinationType.QUEUE, destinationName = "queueErr")
public class TestErroneousMessageListener implements MessageListener {
    @Logging
    private Logger logger;

    @Override
    public void onMessage(Message message) {
        try {
            logger.info("Rollbacking '{}' received", ((TextMessage) message).getText());
        } catch (JMSException e) {
            // ignore
        }
        throw new JMSRollbackException("bad");
    }
}
