/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.seedstack.jms.Whitebox;

@RunWith(MockitoJUnitRunner.class)
public class ManagedMessageConsumerTest {

    private ManagedMessageConsumer underTest;
    @Mock
    private MessageConsumer messageConsumer;
    @Mock
    private Destination destination;
    @Mock
    private ManagedSession session;

    private MyMessageListener messageListener = new MyMessageListener();

    @Before
    public void setUp() throws JMSException {
        underTest = new ManagedMessageConsumer(messageConsumer, destination, null, false, false, session);
        when(session.createConsumer(destination)).thenReturn(messageConsumer);
    }

    @Test
    public void messageConsumer_is_reset_then_refreshed() throws JMSException {
        MessageConsumer actualMessageConsumer = (MessageConsumer) Whitebox.getInternalState(underTest,
                "messageConsumer");
        Assertions.assertThat(actualMessageConsumer).isEqualTo(messageConsumer);
        underTest.setMessageListener(messageListener);

        // Reset message consumer
        underTest.reset();

        actualMessageConsumer = (MessageConsumer) Whitebox.getInternalState(underTest, "messageConsumer");
        Assertions.assertThat(actualMessageConsumer).isNull();

        // Refresh message consumer and the message listeners on cascade
        underTest.refresh(session);
        actualMessageConsumer = (MessageConsumer) Whitebox.getInternalState(underTest, "messageConsumer");
        Assertions.assertThat(actualMessageConsumer).isNotNull();

        // method call at the init and then at the refresh
        verify(messageConsumer, times(2)).setMessageListener(messageListener);
    }

    class MyMessageListener implements MessageListener {
        @Override
        public void onMessage(Message message) {
        }
    }
}
