/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms;

import static org.fest.reflect.core.Reflection.method;
import static org.junit.Assert.fail;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Named;
import javax.jms.Connection;
import javax.jms.JMSException;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.seedstack.jms.fixtures.TestExceptionListener;
import org.seedstack.jms.fixtures.TestSender3;
import org.seedstack.jms.fixtures.TestSender4;
import org.seedstack.jms.internal.FakeConnectionFactoryImpl;
import org.seedstack.jms.spi.ConnectionDefinition;
import org.seedstack.jms.spi.JmsFactory;
import org.seedstack.seed.Logging;
import org.seedstack.seed.testing.junit4.SeedITRunner;
import org.slf4j.Logger;

@RunWith(SeedITRunner.class)
public class JmsRefreshIT {

    // TODO <pith> 19/11/2014: improve these tests.

    @Logging
    Logger logger;

    @Inject
    TestSender3 testSender3;

    @Inject
    TestSender4 testSender4;

    public static CountDownLatch latchConnect1 = new CountDownLatch(1);
    public static CountDownLatch latchReconnect1 = new CountDownLatch(1);

    public static CountDownLatch latchReconnect2 = new CountDownLatch(1);

    public static String text = null;

    @Inject
    @Named("connection3")
    private Connection connection3;

    @Test
    public void reset_then_refresh_connection_should_works() throws Exception {
        // Send succeed
        testSender3.send("MANAGED1");
        latchConnect1.await(1, TimeUnit.SECONDS);
        Assertions.assertThat(text).isEqualTo("MANAGED1");

        // Reset connection

        connection3.close();
        method("onException").withParameterTypes(JMSException.class)
                .in(connection3)
                .invoke(new JMSException("Connection is down"));
        //Thread.sleep(200); // reconnect at the first try

        // Refresh connection and resend message
        boolean sent = false;
        int attempt = 0;
        while (!sent && attempt < 50) {
            try {
                attempt++;
                testSender3.send("RECONNECTED1");
                sent = true;
            } catch (Exception e) {
                Thread.sleep(50);
            }
        }

        latchReconnect1.await(200, TimeUnit.MILLISECONDS);
        Assertions.assertThat(text).isEqualTo("RECONNECTED1"); // message is successfully received
    }

    @Test
    public void connection_failed_multiple_times_then_reconnect() throws InterruptedException, JMSException {
        JmsFactory connectionFactory = (JmsFactory) Whitebox.getInternalState(connection3, "jmsFactoryImpl");
        Whitebox.setInternalState(connection3, "jmsFactoryImpl", new FakeConnectionFactoryImpl());

        connection3.close();
        ((javax.jms.ExceptionListener) connection3).onException(new JMSException("Connection Closed"));

        String message = "MANAGED2";
        for (int i = 0; i < 4; i++) {
            try {
                logger.info("Send message: {}", message);
                testSender3.send(message);
                fail();
            } catch (Exception e) {
                Thread.sleep(10);
                logger.info("Unable to send message: {}", message);
            }
        }

        Whitebox.setInternalState(connection3, "jmsFactoryImpl", connectionFactory);
        Thread.sleep(200); // wait reconnection
        message = "RECONNECT2";
        testSender3.send(message);
        logger.info("Send message: {}", message);
        latchReconnect2.await(100, TimeUnit.MILLISECONDS);
        Assertions.assertThat(text).isEqualTo(message);
    }

    @Test
    public void test_that_wraped_exceptionlistener_from_managedConnection_is_declared_using_props() throws InterruptedException, JMSException {
        Class<? extends javax.jms.ExceptionListener> exceptionListener =
                ((ConnectionDefinition) Whitebox.getInternalState(
                        connection3,
                        "connectionDefinition")).getExceptionListenerClass();
        Assertions.assertThat(exceptionListener).isAssignableFrom(TestExceptionListener.class);
    }

    //                  MANUAL TESTS
    //
    // To test the reconnection feature with an actual broker.
    //
    // /!\ Don't forget to change the broker url in the props file.
    //

    //@Test
    public void manual_test() throws Exception {
        // Send succeed
        testSender3.send("MANAGED1");
        latchConnect1.await(1, TimeUnit.SECONDS);
        Assertions.assertThat(text).isEqualTo("MANAGED1");

        logger.info("You should shutdown the broker...");
        Thread.sleep(10000);
        try {
            testSender3.send("SHOULD FAIL");
            fail();
        } catch (JMSException e) {
            logger.info("Failed to send message");

            logger.info("You should restart the broker...");
            Thread.sleep(10000);
            testSender3.send("RECONNECTED1");
            latchReconnect1.await(1, TimeUnit.SECONDS);
            Assertions.assertThat(text).isEqualTo("RECONNECTED1");
        }
    }

    //@Test
    public void manual_cyclic_fail() throws InterruptedException {
        logger.info("You should shutdown the broker...");
        Thread.sleep(5000);
        String message = "MANAGED1";
        while (true) {
            try {
                testSender3.send(message);
                break;
            } catch (JMSException e) {
                Thread.sleep(100);
                logger.info("Unable to send message: {}", message);
            }
        }
        latchConnect1.await(10, TimeUnit.SECONDS);
        Assertions.assertThat(text).isEqualTo(message);
    }
}
