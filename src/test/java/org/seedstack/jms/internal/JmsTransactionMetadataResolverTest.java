/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;

import java.lang.reflect.Method;

import javax.jms.Message;
import javax.jms.MessageListener;

import org.aopalliance.intercept.MethodInvocation;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mockito.Mockito;
import org.seedstack.jms.JmsConnection;
import org.seedstack.seed.transaction.spi.TransactionMetadata;

/**
 * Unit test for {@link JmsTransactionMetadataResolver}
 * 
 * @author thierry.bouvet@mpsa.com
 *
 */
public class JmsTransactionMetadataResolverTest {

    class NotAJmsListener {
        public void test() {

        }
    }

    class MyJmsListener implements MessageListener {

        @Override
        public void onMessage(Message message) {
        }

        public void myMethod(Message message) {
        }

        public void onMessage() {
        }

        public void onMessage(String s) {
        }

    }

    @Test
    public void testResolveWithoutJmsClass() throws Exception {
        MethodInvocation inv = Mockito.mock(MethodInvocation.class);
        Method m = NotAJmsListener.class.getDeclaredMethod("test");
        Mockito.doReturn(m).when(inv).getMethod();
        JmsTransactionMetadataResolver resolver = new JmsTransactionMetadataResolver();
        Assertions.assertThat(resolver.resolve(inv, null)).isNull();

    }

    @Test
    public void testResolveWithJmsListenerOK() throws Exception {
        MethodInvocation inv = Mockito.mock(MethodInvocation.class);
        Method m = MyJmsListener.class.getDeclaredMethod("onMessage", Message.class);
        Mockito.doReturn(m).when(inv).getMethod();
        JmsTransactionMetadataResolver resolver = new JmsTransactionMetadataResolver();
        TransactionMetadata transactionMetadata = resolver.resolve(inv, null);
        Assertions.assertThat(transactionMetadata).isNotNull();

        Assertions.assertThat(transactionMetadata.getHandler()).isEqualTo(JmsListenerTransactionHandler.class);

    }

    @Test
    public void testResolveWithJmsListenerAndAnotherMethod() throws Exception {
        MethodInvocation inv = Mockito.mock(MethodInvocation.class);
        Method m = MyJmsListener.class.getDeclaredMethod("myMethod", Message.class);
        Mockito.doReturn(m).when(inv).getMethod();
        JmsTransactionMetadataResolver resolver = new JmsTransactionMetadataResolver();
        Assertions.assertThat(resolver.resolve(inv, null)).isNull();

    }

    @Test
    public void testResolveWithJmsListenerAndAnotherMethod2() throws Exception {
        MethodInvocation inv = Mockito.mock(MethodInvocation.class);
        Method m = MyJmsListener.class.getDeclaredMethod("onMessage");
        Mockito.doReturn(m).when(inv).getMethod();
        JmsTransactionMetadataResolver resolver = new JmsTransactionMetadataResolver();
        Assertions.assertThat(resolver.resolve(inv, null)).isNull();

    }

    @Test
    public void testResolveWithJmsListenerAndAnotherMethod3() throws Exception {
        MethodInvocation inv = Mockito.mock(MethodInvocation.class);
        Method m = MyJmsListener.class.getDeclaredMethod("onMessage", String.class);
        Mockito.doReturn(m).when(inv).getMethod();
        JmsTransactionMetadataResolver resolver = new JmsTransactionMetadataResolver();
        Assertions.assertThat(resolver.resolve(inv, null)).isNull();

    }

    @Test
    public void testResolveWithProducer() throws Exception {
        final String producerConnection = "connection";

        class MyProducer {

            @JmsConnection(producerConnection)
            public void produce() {

            }
        }
        MethodInvocation inv = Mockito.mock(MethodInvocation.class);
        Method m = MyProducer.class.getDeclaredMethod("produce");
        Mockito.doReturn(m).when(inv).getMethod();
        JmsTransactionMetadataResolver resolver = new JmsTransactionMetadataResolver();

        TransactionMetadata transactionMetadata = resolver.resolve(inv, null);
        Assertions.assertThat(transactionMetadata).isNotNull();

        Assertions.assertThat(transactionMetadata.getHandler()).isEqualTo(JmsTransactionHandler.class);
        Assertions.assertThat(transactionMetadata.getResource()).isEqualTo(producerConnection);
    }

}
