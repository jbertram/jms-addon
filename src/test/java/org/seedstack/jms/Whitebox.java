/*
 * Copyright © 2013-2020, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms;

import static org.seedstack.shed.reflect.ReflectUtils.getValue;
import static org.seedstack.shed.reflect.ReflectUtils.makeAccessible;
import static org.seedstack.shed.reflect.ReflectUtils.setValue;

public class Whitebox {
    public static <T> T getInternalState(T self, String fieldName) {
        try {
            return getValue(makeAccessible(self.getClass().getDeclaredField(fieldName)), self);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setInternalState(Object self, String fieldName, Object value) {
        try {
            setValue(makeAccessible(self.getClass().getDeclaredField(fieldName)), self, value);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
