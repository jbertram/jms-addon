/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;

import org.aopalliance.intercept.MethodInvocation;
import org.seedstack.jms.JmsConnection;
import org.seedstack.seed.transaction.spi.TransactionMetadata;
import org.seedstack.seed.transaction.spi.TransactionMetadataResolver;

import javax.jms.MessageListener;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * This {@link org.seedstack.seed.transaction.spi.TransactionMetadataResolver}
 * resolves metadata for transactions marked with
 * {@link org.seedstack.jms.JmsConnection} or for the method
 * {@link MessageListener#onMessage(javax.jms.Message)}.
 */
class JmsTransactionMetadataResolver implements TransactionMetadataResolver {
    @Override
    public TransactionMetadata resolve(MethodInvocation methodInvocation, TransactionMetadata defaults) {
        Class<?> declaringClass = methodInvocation.getMethod().getDeclaringClass();
        if (MessageListener.class.isAssignableFrom(declaringClass)) {
            if (compare(methodInvocation.getMethod(), MessageListener.class.getDeclaredMethods()[0])) {
                TransactionMetadata transactionMetadata = new TransactionMetadata();
                transactionMetadata.setHandler(JmsListenerTransactionHandler.class);
                transactionMetadata.setResource(declaringClass.getCanonicalName());

                return transactionMetadata;
            }
        }
        Optional<JmsConnection> jmsConnection = JmsConnectionResolver.INSTANCE.apply(methodInvocation.getMethod());

        if (jmsConnection.isPresent()) {
            TransactionMetadata transactionMetadata = new TransactionMetadata();
            transactionMetadata.setHandler(JmsTransactionHandler.class);
            transactionMetadata.setResource(jmsConnection.get().value());

            return transactionMetadata;
        }

        return null;
    }

    private boolean compare(Method o1, Method o2) {
        if (!o1.getName().equals(o2.getName())) {
            return false;
        }
        if (o1.getParameterTypes().length != o2.getParameterTypes().length) {
            return false;
        }
        final Class<?>[] parameterTypes1 = o1.getParameterTypes();
        final Class<?>[] parameterTypes2 = o2.getParameterTypes();
        for (int i = 0; i < parameterTypes1.length; i++) {
            if (!parameterTypes1[i].equals(parameterTypes2[i])) {
                return false;
            }
        }
        return true;
    }

}
