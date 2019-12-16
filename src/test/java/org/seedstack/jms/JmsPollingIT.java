/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms;

import com.google.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.seedstack.jms.fixtures.TestSender4;

import javax.jms.JMSException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.seedstack.seed.testing.junit4.SeedITRunner;

import static org.junit.Assert.fail;

@RunWith(SeedITRunner.class)
public class JmsPollingIT {
    @Inject
    TestSender4 testSender4;

    public static CountDownLatch count = new CountDownLatch(1);
    public static String text = null;

    /**
     * TestSender4 and TestMessageListener4.
     */
    @Test
    public void message_polling_is_working() throws JMSException {
        testSender4.send("HELLO");

        try {
            count.await(5, TimeUnit.SECONDS);

            Assertions.assertThat(text).isEqualTo("HELLO");
        } catch (InterruptedException e) {
            fail("Thread interrupted");
        }
    }
}
