/*
 * Copyright © 2013-2020, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;

import java.util.ArrayDeque;
import java.util.Deque;
import javax.jms.Session;
import org.seedstack.seed.transaction.spi.TransactionalLink;

class JmsSessionLink implements TransactionalLink<Session> {
    private final ThreadLocal<Deque<Session>> sessionThreadLocal;

    JmsSessionLink() {
        sessionThreadLocal = ThreadLocal.withInitial(ArrayDeque::new);
    }

    @Override
    public Session get() {
        Session session = sessionThreadLocal.get().peek();
        if (session == null) {
            throw new IllegalStateException("Attempt to use a JMS session without a transaction");
        }

        return session;
    }

    Session getCurrentTransaction() {
        return sessionThreadLocal.get().peek();
    }

    void push(Session session) {
        sessionThreadLocal.get().push(session);
    }

    Session pop() {
        Deque<Session> sessions = sessionThreadLocal.get();
        Session session = sessions.pop();
        if (sessions.isEmpty()) {
            sessionThreadLocal.remove();
        }
        return session;
    }
}
