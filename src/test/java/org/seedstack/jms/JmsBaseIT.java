/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package org.seedstack.jms;

import static org.junit.Assert.fail;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.jms.Connection;
import javax.jms.JMSException;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.seedstack.seed.testing.ConfigurationProperty;
import org.seedstack.seed.testing.SystemProperty;
import org.seedstack.seed.testing.junit4.SeedITRunner;
import senders.TestSender1;
import senders.TestSender2;
import senders.TestSenderErr;

@RunWith(SeedITRunner.class)
@SystemProperty(name = "seedstack.config.application.basePackages", value = "senders")
public class JmsBaseIT {

    @Inject
    TestSender1 testSender1;

    @Inject
    TestSender2 testSender2;

    @Inject
    TestSenderErr testSenderErr;

    @Inject
    Injector injector;

    public static CountDownLatch managed = new CountDownLatch(1);
    public static CountDownLatch unmanaged = new CountDownLatch(1);
    public static String textManaged = null;
    public static String textUnmanaged = null;

    /**
     * TestSender1 and TestMessageListener1.
     */
    @Test
    public void managed_send_and_receive_is_working() throws JMSException {
        testSender1.send("MANAGED");

        try {
            managed.await(1, TimeUnit.SECONDS);

            Assertions.assertThat(textManaged).isEqualTo("MANAGED");
        } catch (InterruptedException e) {
            fail("Thread interrupted");
        }
    }

    /**
     * TestSender2 and TestMessageListener2.
     */
    @Test
    public void unmanaged_send_and_receive_is_working() throws JMSException {

        testSender2.send("UNMANAGED");

        try {
            unmanaged.await(1, TimeUnit.SECONDS);

            Assertions.assertThat(textUnmanaged).isEqualTo("UNMANAGED");
        } catch (InterruptedException e) {
            fail("Thread interrupted");
        }
    }

    /**
     * TestSenderErr and TestMessageListenerErr.
     */
    @Test
    public void managed_send_and_receive_failure() throws JMSException, InterruptedException {
        testSenderErr.send("MANAGED");
        Thread.sleep(1000);
    }

    @Test
    public void connections_are_singletons() throws JMSException {
        Assertions.assertThat(injector.getInstance(Key.get(Connection.class, Names.named("connection1"))))
                .isSameAs(injector.getInstance(Key.get(Connection.class, Names.named("connection1"))));
    }
}
