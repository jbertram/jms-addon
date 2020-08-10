/*
 * Copyright © 2013-2020, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.fixtures;

import javax.jms.Message;
import org.seedstack.jms.DestinationType;
import org.seedstack.jms.JmsMessageListener;
import org.seedstack.seed.Logging;
import org.seedstack.seed.transaction.Transactional;
import org.slf4j.Logger;

@JmsMessageListener(connection = "connection99", destinationType = DestinationType.DISABLED,
        destinationName = "${test.dest1.name}")
public class DisabledMessageListener implements javax.jms.MessageListener {
    @Logging
    public Logger logger;

    @Override
    @Transactional
    public void onMessage(Message message) {
        throw new IllegalStateException("should not be reached");
    }
}