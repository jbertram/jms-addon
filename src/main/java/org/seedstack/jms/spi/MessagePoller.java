/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.spi;


import javax.jms.ExceptionListener;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;

/**
 * Interface for message pollers.
 */
public interface MessagePoller {
    void setSession(Session session);

    void setMessageConsumer(MessageConsumer messageConsumer);

    void setExceptionListener(ExceptionListener exceptionListener);

    void setMessageListener(MessageListener messageListener);

    void start();

    void stop();

}
