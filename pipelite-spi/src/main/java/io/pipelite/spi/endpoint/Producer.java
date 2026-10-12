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
package io.pipelite.spi.endpoint;

import io.pipelite.dsl.Exchange;

/**
 * What an adapter implements to write an exchange to its destination (a {@code toSink(...)} or a route
 * destination). The framework wraps it in its own node of the flow, which applies the flow's error
 * handling around {@link #produce(Exchange)}: a runtime exception thrown here is a failure of the
 * step, handled like the failure of any processor (retry, dead letter).
 * <p>
 * A producer that holds a resource (a connection, a client) releases it in {@link #close()}, which the
 * framework calls once the producer is no longer needed: when the context stops for a producer that
 * ends a flow, right after the exchange for one created to deliver a single exchange.
 */
@FunctionalInterface
public interface Producer extends AutoCloseable {

    void produce(Exchange exchange);

    /** Releases what the producer holds. Does nothing by default. */
    @Override
    default void close() {
    }

}
