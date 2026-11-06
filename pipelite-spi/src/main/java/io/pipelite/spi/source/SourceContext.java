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
package io.pipelite.spi.source;

import io.pipelite.dsl.Exchange;

import java.util.Map;
import java.util.concurrent.ThreadFactory;

/** The handles the framework gives a {@link Source}. */
public interface SourceContext {

    /** The name of the flow this source feeds. */
    String flowName();

    /** A new exchange whose input payload is {@code payload}, with no header of the transport. */
    default Exchange newExchange(Object payload) {
        return newExchange(payload, Map.of());
    }

    /**
     * A new exchange whose input payload is {@code payload}, with the headers of what the source received: the headers
     * of an HTTP request, of a Kafka record, and whatever else the protocol says about it that a flow may want to read
     * (the method, the topic, the offset...).
     * <p>
     * The source hands over <em>all</em> of them, as they are, with the names the protocol gives them: it does not choose.
     * The framework puts on the exchange only the ones the flow declared with {@code propagateHeaders(...)}, none by
     * default, ignoring the case of the names, so every adapter behaves the same way and none has a filter of its own.
     * A value that is not text is converted by the source before it hands it over.
     */
    Exchange newExchange(Object payload, Map<String, String> transportHeaders);

    /**
     * Hands an exchange to the framework. It returns when the exchange has been accepted: written to the
     * durable inbox and queued, applying the backpressure of the flow. It throws when the exchange could
     * not be accepted, and the source must then not acknowledge its input (a Kafka source does not commit
     * the offsets).
     */
    void accept(Exchange exchange);

    /** A factory of threads named after the flow, for a source that needs one of its own. */
    ThreadFactory threadFactory();

    /** Whether the flow is running: false once it is stopping, and a source thread must then end. */
    boolean isRunning();
}
