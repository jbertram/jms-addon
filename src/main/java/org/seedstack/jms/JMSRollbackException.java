/*
 * Copyright © 2013-2019, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms;

public class JMSRollbackException extends RuntimeException {
    public JMSRollbackException() {
    }

    public JMSRollbackException(String message) {
        super(message);
    }

    public JMSRollbackException(String message, Throwable cause) {
        super(message, cause);
    }

    public JMSRollbackException(Throwable cause) {
        super(cause);
    }

    public JMSRollbackException(String message, Throwable cause, boolean enableSuppression,
            boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
