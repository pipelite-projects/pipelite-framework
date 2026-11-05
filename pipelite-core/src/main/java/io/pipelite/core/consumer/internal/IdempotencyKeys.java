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
import io.pipelite.core.correlation.CorrelationKeys;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.dsl.correlation.Correlation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fixes the idempotency key of an exchange (issue #40) where it enters a flow. An exchange that already has one keeps
 * it: it was handed over by another flow, or it is the one the durable inbox gave back after a restart, and what makes
 * the key useful is that it is the same each time the work is repeated.
 */
final class IdempotencyKeys {

    private static final Logger SYS_LOGGER = LoggerFactory.getLogger(IdempotencyKeys.class);

    private IdempotencyKeys() {
    }

    /** Gives {@code exchange} a key, if it has none: the id of the message it came in with. */
    static void ensure(ExchangeImpl exchange) {
        ensure(exchange, null, null);
    }

    /**
     * Gives {@code exchange} a key, if it has none: what {@code declared} reads from it, when the source declared how, or
     * else the id of the message it came in with. A request the declaration gives no value for gets the generated key,
     * and a warning: the guarantee of the declared key holds for the requests that have it.
     */
    static void ensure(ExchangeImpl exchange, Correlation declared, String flowName) {
        if (exchange.getProperty(IOKeys.IDEMPOTENCY_KEY_PROPERTY_NAME, String.class) != null) {
            return;
        }
        String key = null;
        if (declared != null) {
            try {
                key = CorrelationKeys.keyOf(declared, exchange);
            } catch (RuntimeException unreadable) {
                SYS_LOGGER.warn("Flow '{}': an exchange has no idempotency key to read from it ({}), a generated one is used. {}",
                    flowName, declared, unreadable.getMessage());
            }
        }
        exchange.setProperty(IOKeys.IDEMPOTENCY_KEY_PROPERTY_NAME, key != null ? key : exchange.getInput().getId());
    }
}
