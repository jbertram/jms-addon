/**
 * Copyright (c) 2013-2015, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.fixtures;

import org.seedstack.jms.DestinationType;
import org.seedstack.jms.JmsMessageListener;
import org.seedstack.seed.Logging;
import org.seedstack.jms.JmsRefreshIT;
import org.seedstack.seed.transaction.Transactional;
import org.slf4j.Logger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

@JmsMessageListener(connection = "connection3", destinationType = DestinationType.QUEUE, destinationName = "queue3")
public class TestMessageListener3 implements MessageListener {

    @Logging
    Logger logger;

    @Override
    @Transactional
    public void onMessage(Message message) {
        try {
            String text = ((TextMessage) message).getText();
            JmsRefreshIT.text = text;
            logger.info("Message '{}' received", ((TextMessage) message).getText());
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
        if (JmsRefreshIT.text.equals("MANAGED1")) {
            JmsRefreshIT.latchConnect1.countDown();
        } else if (JmsRefreshIT.text.equals("RECONNECT1")) {
            JmsRefreshIT.latchReconnect1.countDown();
        } else if (JmsRefreshIT.text.equals("RECONNECT2")) {
            JmsRefreshIT.latchReconnect2.countDown();
        }
    }
}
