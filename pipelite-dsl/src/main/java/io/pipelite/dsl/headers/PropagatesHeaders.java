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
package io.pipelite.dsl.headers;

/**
 * A capability of a {@code SourceConfigurer} or of a {@code SinkConfigurer}: the endpoint it configures has headers (an
 * HTTP request, a Kafka record), so the flow can choose which of them cross the border between the flow and the outside:
 * {@code (KafkaSourceConfigurer c) -> c.propagateHeaders(h -> h.filter("Idempotency-Key", "X-Tenant-Id"))}, or {@code
 * h.all()}.
 * <p>
 * It is an interface, not a method of the base class, because not every endpoint has headers (a file, a timer, a log) and
 * a configurer of one that has them can offer other capabilities too. Only the configurers of the adapters whose protocol
 * has headers implement it, so a flow cannot declare the headers of an endpoint that has none: it does not compile. The
 * framework does not know any adapter: it asks every configurer whether it implements this interface.
 * <p>
 * An interface cannot hold state, so the implementing class keeps the value in a field of its own and exposes it through
 * the two accessors, which are not meant to be called from user code. What the user calls is {@link #propagateHeaders}.
 */
public interface PropagatesHeaders {

    /**
     * What {@link #propagateHeaders} declared: none, when it was not called. For the framework: like the other typed settings it
     * cannot travel in the query string of the URL, so the framework reads it from here.
     */
    HeaderPropagation headerPropagation();

    /** Keeps what {@link #propagateHeaders} declared. For {@link #propagateHeaders} only: not meant to be called from user code. */
    void setHeaderPropagation(HeaderPropagation propagation);

    /**
     * Chooses which headers cross the border: the ones named, with the names written here (the case is ignored when they are
     * looked up), or all of them. By default none does: the headers of a request or of an exchange are written to disk with
     * it and a request carries credentials, so a flow takes the ones it needs.
     * <p>
     * On a source they are the headers of the protocol that the source puts on the exchange; on a sink, the headers of the
     * exchange that the sink hands to the other system, in the form its protocol has.
     */
    default void propagateHeaders(HeaderPropagationConfigurator headers) {
        setHeaderPropagation(HeaderPropagation.from(headers));
    }
}
