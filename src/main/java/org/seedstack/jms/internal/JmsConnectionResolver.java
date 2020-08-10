/*
 * Copyright © 2013-2020, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;

import org.seedstack.jms.JmsConnection;
import org.seedstack.shed.reflect.StandardAnnotationResolver;

import java.lang.reflect.Method;

class JmsConnectionResolver extends StandardAnnotationResolver<Method, JmsConnection> {
    static JmsConnectionResolver INSTANCE = new JmsConnectionResolver();

    private JmsConnectionResolver() {
        // no external instantiation allowed
    }
}
