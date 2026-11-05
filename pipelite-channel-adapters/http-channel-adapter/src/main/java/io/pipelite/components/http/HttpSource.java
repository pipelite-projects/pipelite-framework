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
package io.pipelite.components.http;

import io.pipelite.spi.source.EventSource;
import io.pipelite.spi.source.SourceContext;

import java.util.Map;

/**
 * The {@code http://<resource>} source of a flow: the adapter's server hands it the body of every request
 * made to its resource, and it hands the body over to the flow as the payload of a new exchange.
 */
public final class HttpSource implements EventSource {

    private final String allowedMethod;
    private volatile SourceContext context;

    HttpSource(String allowedMethod) {
        this.allowedMethod = allowedMethod;
    }

    /** The only HTTP method the resource accepts, or {@code null} when any is accepted. */
    public String allowedMethod() {
        return allowedMethod;
    }

    @Override
    public void start(SourceContext context) {
        this.context = context;
    }

    @Override
    public void stop() {
    }

    /**
     * Hands the body of a request to the flow. It returns when the exchange has been accepted (written to
     * the durable inbox and queued), not when the flow has processed it, and it throws when it could not be
     * accepted.
     */
    public void accept(String body) {
        accept(body, Map.of());
    }

    /**
     * Hands the body of a request to the flow, with all the headers of the request and what is known of it (see {@link
     * HttpConstants}): the framework puts on the exchange only the ones the flow asked for with {@code propagateHeaders}.
     */
    public void accept(String body, Map<String, String> transportHeaders) {
        final SourceContext started = context;
        if (started == null) {
            throw new IllegalStateException("The http source has not been started");
        }
        started.accept(started.newExchange(body, transportHeaders));
    }
}
