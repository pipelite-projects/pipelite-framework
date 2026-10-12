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
package io.pipelite.core.source.internal;

import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.source.EventSource;
import io.pipelite.spi.source.PollingSource;
import io.pipelite.spi.source.Source;

import java.util.Objects;

/**
 * Wraps the {@link Source} an adapter provides in the consumer that starts a flow. The adapter's source
 * only receives; the queue, the workers, the backpressure, the durable inbox and the thread naming are
 * the framework's, and come from the consumer machinery this wraps.
 */
public final class SourceConsumers {

    private SourceConsumers() {
    }

    /**
     * @param role names the threads of the source: {@code pipelite-<role>-<abbreviated flow name>}, the
     *             protocol of the source URL ({@code kafka}, {@code time}, {@code file}...)
     */
    public static Consumer wrap(Source source, Endpoint endpoint, String role) {
        Objects.requireNonNull(source, "source is required and cannot be null");
        Objects.requireNonNull(endpoint, "endpoint is required and cannot be null");
        Objects.requireNonNull(role, "role is required and cannot be null");
        if (source instanceof EventSource) {
            return new EventSourceService((EventSource) source, endpoint, role);
        }
        return PollingSourceService.create((PollingSource) source, endpoint, role);
    }
}
