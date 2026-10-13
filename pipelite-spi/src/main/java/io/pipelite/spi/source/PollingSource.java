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

/**
 * A source the framework polls on a schedule (the {@code initialDelay}, {@code period}, {@code
 * timeUnit} and {@code batchSize} of the endpoint). The time and file sources are polling sources.
 */
public non-sealed interface PollingSource extends Source {

    /** Keeps the context and opens whatever the source needs. Called before the first poll. */
    default void start(SourceContext context) {
    }

    /**
     * The next exchange, created with {@link SourceContext#newExchange(Object)}, or {@code null} when
     * there is nothing to read now. Called up to {@code batchSize} times per tick, until it returns
     * {@code null}.
     * <p>
     * A source that reads several records at a time, and must not move its own position (a file offset)
     * before they are safe, hands each of them over with {@link SourceContext#accept}, which makes it
     * durable, and returns {@code null}: the framework then takes the exchanges from its own queue.
     */
    Exchange poll();

    /** Releases what {@link #start} opened. Called after the polling has stopped. */
    default void stop() {
    }
}
