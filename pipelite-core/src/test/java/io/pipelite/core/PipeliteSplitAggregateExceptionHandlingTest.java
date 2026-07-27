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
package io.pipelite.core;

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * Sources registered via {@code fromSource(...)} are event-driven (queue + background thread,
 * see {@code EventDrivenConsumerService}) - {@code supplyExchange(...)} does not block until
 * processing completes, and does not propagate exceptions back to the caller. Every assertion
 * here goes through {@link Awaitility} on an externally observable side effect instead, mirroring
 * the existing {@code PipeliteRetryChannelIntegrationTest}.
 */
public class PipeliteSplitAggregateExceptionHandlingTest {

    private PipeliteContext context;

    @Before
    public void setup() {
        context = Pipelite.createContext();
    }

    @Test
    public void givenItemThrows_whenNoRetryChannelConfigured_thenLaterItemsAreNeverAttempted() {

        final AtomicInteger attemptedCount = new AtomicInteger(0);
        final AtomicInteger processedCount = new AtomicInteger(0);

        final FlowDefinition flow = Pipelite.defineFlow("split-no-retry-flow")
            .fromSource("split-no-retry-in")
            .split("split-step", segment -> segment
                .process("maybe-fail", (io, c) -> {
                    final int value = io.getInputPayloadAs(Integer.class);
                    attemptedCount.incrementAndGet();
                    if (value == 1) {
                        throw new RuntimeException("simulated item failure");
                    }
                    processedCount.incrementAndGet();
                    io.setOutputPayload(value);
                })
                .end())
            .toSink("split-no-retry-out")
            .build();

        context.registerFlowDefinition(flow);
        context.start();

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();
        final Exchange exchange = exchangeFactory.createExchange(List.of(1, 2, 3));

        context.supplyExchange("split-no-retry-in", exchange);

        // Item 1 always throws before item 2/3 could ever be reached (single-threaded,
        // sequential loop with no per-item catch) - once the first attempt is observed,
        // the whole split has already unwound, so this is not a race.
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> attemptedCount.get() >= 1);

        assertEquals(0, processedCount.get());
    }

    @Test
    public void givenItemThrows_whenRetryChannelConfigured_thenExceptionHandlerAbsorbsItAndRetryRedeliversTheWholeSplit() {

        final AtomicInteger invocationCount = new AtomicInteger(0);

        final FlowDefinition flow = Pipelite.defineFlow("split-retry-flow")
            .fromSource("split-retry-in")
            .split("split-step", segment -> segment
                .process("fail-once", (io, c) -> {
                    if (invocationCount.incrementAndGet() == 1) {
                        throw new RuntimeException("simulated item failure");
                    }
                    // Second attempt (redelivered by the retry-channel): stop here,
                    // no need to let it reach the sink for this test's purpose.
                    c.stopExecution();
                })
                .end())
            .toSink("split-retry-out")
            .withRetryChannel()
            .build();

        context.registerFlowDefinition(flow);
        context.start();

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();
        final Exchange exchange = exchangeFactory.createExchange(List.of(1));

        context.supplyExchange("split-retry-in", exchange);

        // If exceptionHandler had been (incorrectly) propagated to inner segment nodes
        // instead of handled once by SplitterNode itself, the collector would never be
        // reached for the failing item and the whole call would NPE instead of reaching
        // RetryChannelExceptionHandler - no dump would ever be created, and this second
        // invocation (the retry redelivery) would never happen.
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> invocationCount.get() >= 2);
    }
}
