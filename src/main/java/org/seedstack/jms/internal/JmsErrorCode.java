/**
 * Copyright (c) 2013-2016, The SeedStack authors <http://seedstack.org>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.seedstack.jms.internal;

import org.seedstack.seed.ErrorCode;

/**
 * JMS error codes.
 */
enum JmsErrorCode implements ErrorCode {
    INVALID_MESSAGE_LISTENER_CLASS,
    CANNOT_SET_CLIENT_ID_IN_JEE_MODE,
    DUPLICATE_CONNECTION_NAME,
    DUPLICATE_MESSAGE_LISTENER_NAME,
    JNDI_LOOKUP_ERROR,
    MESSAGE_POLLER_REQUIRED_IN_JEE_MODE,
    MISCONFIGURED_CONNECTION_FACTORY,
    MISSING_CONNECTION_FACTORY,
    MISSING_JNDI_CONTEXT,
    NO_JNDI_CONTEXT,
    PROPERTY_NOT_FOUND,
    UNABLE_TO_CREATE_CONNECTION_FACTORY,
    UNABLE_TO_CREATE_DESTINATION,
    UNABLE_TO_CREATE_JMS_CONNECTION,
    UNABLE_TO_CREATE_MESSAGE_CONSUMER,
    UNABLE_TO_CREATE_POLLER,
    UNABLE_TO_CREATE_SESSION,
    UNABLE_TO_SET_PROPERTY,
    UNABLE_TO_START_JMS_CONNECTION,
    UNKNOWN_DESTINATION_TYPE,
    UNRECOGNIZED_CONNECTION_FACTORY
}
