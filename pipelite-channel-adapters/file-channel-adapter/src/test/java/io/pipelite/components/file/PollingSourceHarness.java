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
package io.pipelite.components.file;

import io.pipelite.dsl.Exchange;
import io.pipelite.spi.source.PollingSource;
import io.pipelite.spi.source.SourceContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;

/**
 * What the framework does around a {@link PollingSource}, reduced to what the tests of a source need:
 * it starts the source, gives it a context whose {@code accept} queues the exchanges, and answers {@link
 * #receive(long)} the way the framework's polling consumer does: what is queued first, then the source,
 * then what the source handed over while it was polled.
 */
final class PollingSourceHarness implements SourceContext {

    private final PollingSource source;
    private final Deque<StubExchange> queue = new ArrayDeque<>();

    PollingSourceHarness(PollingSource source) {
        this.source = source;
        source.start(this);
    }

    StubExchange receive(long timeout) {
        final StubExchange queued = queue.poll();
        if (queued != null) {
            return queued;
        }
        final Exchange polled = source.poll();
        if (polled != null) {
            return (StubExchange) polled;
        }
        return queue.poll();
    }

    StubExchange receive() {
        return receive(0);
    }

    @Override
    public String flowName() {
        return "test-flow";
    }

    @Override
    public Exchange newExchange(Object payload) {
        final StubExchange exchange = new StubExchange();
        if (payload != null) {
            exchange.setInputPayload(payload);
        }
        return exchange;
    }

    @Override
    public void accept(Exchange exchange) {
        queue.add((StubExchange) exchange);
    }

    @Override
    public ThreadFactory threadFactory() {
        return Thread::new;
    }

    @Override
    public boolean isRunning() {
        return true;
    }
}
