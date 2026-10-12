/*
 * Copyright (C) 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pipelite.core.flow;

import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.ExchangeImpl;

/**
 * What the framework records on an exchange when a step fails, before it hands it to a retry channel or a
 * dead-letter channel: the type and the message of the exception. They are properties of the exchange, not
 * headers (issue #159): they are the framework's own information, which travels with the exchange and its
 * copies, not something user code reads, writes or removes.
 */
final class FailureProperties {

    private FailureProperties() {
    }

    static void record(ExchangeImpl exchange, Throwable failure) {
        exchange.setProperty(IOKeys.FAILURE_EXCEPTION_TYPE_PROPERTY_NAME, failure.getClass());
        // Exchange properties do not hold null: an exception without a message records none.
        if (failure.getMessage() != null) {
            exchange.setProperty(IOKeys.FAILURE_EXCEPTION_MESSAGE_PROPERTY_NAME, failure.getMessage());
        } else {
            exchange.removeProperty(IOKeys.FAILURE_EXCEPTION_MESSAGE_PROPERTY_NAME);
        }
    }
}
