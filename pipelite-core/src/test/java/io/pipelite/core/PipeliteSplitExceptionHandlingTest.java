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


import io.pipelite.core.context.ConfigurablePipeliteContext;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.dsl.definition.FlowDefinition;
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
public class PipeliteSplitExceptionHandlingTest {

    private ConfigurablePipeliteContext context;

    @Before
    public void setup() {
        context = (ConfigurablePipeliteContext) Pipelite.createContext();
        // Retry-routing mechanics only, not persistence - in-memory keeps this hermetic
        // (DefaultPipeliteContext's own default is now file-backed under PipeliteHome, #68).
        context.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());
    }

    @Test
    public void givenAnElementThrows_whenNoErrorHandlingConfigured_thenTheDefaultHandlerLogsItAndTheOtherElementsGoOn() {

        final AtomicInteger attemptedCount = new AtomicInteger(0);
        final AtomicInteger processedCount = new AtomicInteger(0);

        final FlowDefinition flow = Pipelite.defineFlow("split-no-retry-flow")
            .fromSource("queue://split-no-retry-in")
            .split("split-step")
            .process("maybe-fail", (io, c) -> {
                final int value = io.getInputPayloadAs(Integer.class);
                attemptedCount.incrementAndGet();
                if (value == 1) {
                    throw new RuntimeException("simulated element failure");
                }
                processedCount.incrementAndGet();
            })
            .build();

        context.registerFlowDefinition(flow);
        context.start();

        context.supplyExchange("queue://split-no-retry-in", context.createExchange(List.of(1, 2, 3)));

        // Element 1 is the first and it throws: the default handler of the flow logs the failure and stops that
        // element, and the elements after it are sent all the same.
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> attemptedCount.get() >= 3);

        assertEquals(2, processedCount.get());
    }

    @Test
    public void givenAnElementThrows_whenARetryChannelIsConfigured_thenOnlyThatElementIsRetriedAndTheOthersGoOn() {

        final AtomicInteger failingAttempts = new AtomicInteger(0);
        final java.util.Map<Integer, AtomicInteger> processed = new java.util.concurrent.ConcurrentHashMap<>();

        final FlowDefinition flow = Pipelite.defineFlow("split-retry-flow")
            .fromSource("queue://split-retry-in")
            .split("split-step")
            .process("fail-once", (io, c) -> {
                final int value = io.getInputPayloadAs(Integer.class);
                if (value == 1 && failingAttempts.incrementAndGet() == 1) {
                    throw new RuntimeException("simulated element failure");
                }
                processed.computeIfAbsent(value, key -> new AtomicInteger()).incrementAndGet();
            })
            .withRetry(retry -> retry.onErrorChannel(err -> err.toDLQ()))
            .build();

        context.registerFlowDefinition(flow);
        context.start();

        context.supplyExchange("queue://split-retry-in", context.createExchange(List.of(1, 2, 3)));

        // The failing element is handled for itself and retried alone; the elements after it were sent on the
        // first pass and are not run again: each one went through exactly once.
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processed.size() == 3);
        assertEquals(2, failingAttempts.get());
        assertEquals(1, processed.get(1).get());
        assertEquals(1, processed.get(2).get());
        assertEquals(1, processed.get(3).get());
    }
}
