/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Set;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.seedstack.jms.Whitebox;

@RunWith(MockitoJUnitRunner.class)
public class ManagedSessionTest {

    private ManagedSession underTest;
    @Mock
    private ManagedConnection connection;
    @Mock
    private Session session;
    @Mock
    private Destination destination;

    @Before
    public void setUp() throws JMSException {
        underTest = new ManagedSession(session, true, Session.AUTO_ACKNOWLEDGE, false, connection);
        Mockito.when(session.createConsumer(destination, null, false)).thenReturn(Mockito.mock(MessageConsumer.class));
    }

    @Test
    public void session_is_reset() throws JMSException {
        // Check the session state
        Session actualSession = (Session) Whitebox.getInternalState(underTest, "session");
        assertThat(actualSession).isNotNull();

        // Create two consumers
        underTest.createConsumer(destination);
        underTest.createConsumer(destination);
        Set<ManagedMessageConsumer> managedMessageConsumers = (Set<ManagedMessageConsumer>) Whitebox.getInternalState(
                underTest,
                "messageConsumers");
        assertThat(managedMessageConsumers).hasSize(2);

        // Mock the message consumers
        ManagedMessageConsumer messageConsumer1 = Mockito.mock(ManagedMessageConsumer.class);
        ManagedMessageConsumer messageConsumer2 = Mockito.mock(ManagedMessageConsumer.class);
        Whitebox.setInternalState(underTest,
                "messageConsumers",
                Sets.newConcurrentHashSet(Lists.newArrayList(messageConsumer1, messageConsumer2)));

        // reset the connection and the message consumers on cascade
        underTest.reset();
        actualSession = (Session) Whitebox.getInternalState(underTest, "session");
        assertThat(actualSession).isNull();
        Mockito.verify(messageConsumer1, Mockito.times(1)).reset();
        Mockito.verify(messageConsumer2, Mockito.times(1)).reset();
    }

    @Test
    public void session_is_refreshed() throws JMSException {
        // Mock
        ManagedMessageConsumer messageConsumer1 = Mockito.mock(ManagedMessageConsumer.class);
        ManagedMessageConsumer messageConsumer2 = Mockito.mock(ManagedMessageConsumer.class);
        Whitebox.setInternalState(underTest,
                "messageConsumers",
                Sets.newConcurrentHashSet(Lists.newArrayList(messageConsumer1, messageConsumer2)));
        Mockito.when(connection.createSession(true, Session.AUTO_ACKNOWLEDGE)).thenReturn(session);

        // Create two consumers
        underTest.createConsumer(destination);
        underTest.createConsumer(destination);

        // reset session
        underTest.reset();

        // refresh session
        underTest.refresh(connection);
        Session actualSession = (Session) Whitebox.getInternalState(underTest, "session");
        assertThat(actualSession).isNotNull();

        // refresh consumers on cascade
        Mockito.verify(messageConsumer1, Mockito.times(1)).refresh(actualSession);
        Mockito.verify(messageConsumer2, Mockito.times(1)).refresh(actualSession);
    }

    @Test
    public void consumerIsRemovedFromSessionAfterClose() throws Exception {
        MessageConsumer consumer = underTest.createConsumer(destination);
        assertThat((Set<ManagedMessageConsumer>) Whitebox.getInternalState(underTest,
                "messageConsumers")).containsExactly((ManagedMessageConsumer) consumer);
        consumer.close();
        assertThat((Set<ManagedMessageConsumer>) Whitebox.getInternalState(underTest, "messageConsumers")).isEmpty();
    }
}
