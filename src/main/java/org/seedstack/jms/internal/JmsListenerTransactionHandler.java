/*
 * Copyright © 2013-2020, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;


import org.seedstack.seed.transaction.spi.TransactionMetadata;

import javax.jms.Session;

class JmsListenerTransactionHandler extends AbstractJmsTransactionHandler {
    private final Session session;

    JmsListenerTransactionHandler(Session session) {
        this.session = session;
    }

    @Override
    public void doInitialize(TransactionMetadata transactionMetadata) {
        // nothing to do
    }

    @Override
    public Session doCreateTransaction() {
        return session;
    }

    @Override
    public void doCleanup() {
        // nothing to do
    }

    @Override
    public Session getCurrentTransaction() {
        return null;
    }
}
