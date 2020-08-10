/*
 * Copyright © 2013-2020, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.fixtures;

import org.seedstack.jms.JmsPollingIT;
import org.seedstack.jms.DestinationType;
import org.seedstack.jms.JmsMessageListener;
import org.seedstack.seed.Logging;
import org.seedstack.jms.pollers.SimpleMessagePoller;
import org.seedstack.seed.transaction.Transactional;
import org.slf4j.Logger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;


@JmsMessageListener(connection = "connection4", destinationType = DestinationType.QUEUE, destinationName = "queue4", poller = SimpleMessagePoller.class)
public class TestMessageListener4 implements javax.jms.MessageListener {

    @Logging
    public Logger logger;

    @Override
    @Transactional
    public void onMessage(Message message) {
        try {
            JmsPollingIT.text = ((TextMessage) message).getText();
            logger.info("Message '{}' received", ((TextMessage) message).getText());
        } catch (JMSException e) {
            throw new RuntimeException(e);
        } finally {
            JmsPollingIT.count.countDown();
        }
    }
}