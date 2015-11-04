/**
 * Copyright (c) 2013-2015, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.fixtures;

import org.seedstack.jms.JmsBaseIT;
import org.seedstack.jms.DestinationType;
import org.seedstack.jms.JmsMessageListener;
import org.seedstack.seed.Logging;
import org.slf4j.Logger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;


@JmsMessageListener(connection = "connection2", destinationType = DestinationType.QUEUE, destinationName = "queue2")
public class TestMessageListener2 implements MessageListener {

    @Logging
    public Logger logger;

    @Override
    public void onMessage(Message message) {
        try {
            JmsBaseIT.textUnmanaged = ((TextMessage) message).getText();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        } finally {
            JmsBaseIT.unmanaged.countDown();
        }
    }
}