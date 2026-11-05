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
package io.pipelite.core.consumer.internal;

import io.pipelite.core.context.internal.IOKeys;
import io.pipelite.core.exchange.internal.ExchangeImpl;

/**
 * Fixes the idempotency key of an exchange (issue #40) where it enters a flow. An exchange that already has one keeps
 * it: it was handed over by another flow, or it is the one the durable inbox gave back after a restart, and what makes
 * the key useful is that it is the same each time the work is repeated.
 */
final class IdempotencyKeys {

    private IdempotencyKeys() {
    }

    /** Gives {@code exchange} a key, if it has none: the id of the message it came in with. */
    static void ensure(ExchangeImpl exchange) {
        if (exchange.getProperty(IOKeys.IDEMPOTENCY_KEY_PROPERTY_NAME, String.class) == null) {
            exchange.setProperty(IOKeys.IDEMPOTENCY_KEY_PROPERTY_NAME, exchange.getInput().getId());
        }
    }
}
