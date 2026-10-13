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
package io.pipelite.components.time;

import io.pipelite.dsl.Exchange;
import io.pipelite.spi.source.PollingSource;
import io.pipelite.spi.source.SourceContext;

import java.time.LocalDateTime;

/**
 * The {@code time://} source: every poll is a tick whose payload is the instant it fires. The schedule
 * ({@code initialDelay}, {@code period}, {@code timeUnit}, {@code batchSize}) is the framework's.
 */
final class TimeSource implements PollingSource {

    private SourceContext context;

    @Override
    public void start(SourceContext context) {
        this.context = context;
    }

    /** Never {@code null}: the framework stops a {@code batchSize} loop at the first {@code null}. */
    @Override
    public Exchange poll() {
        return context.newExchange(LocalDateTime.now());
    }
}
