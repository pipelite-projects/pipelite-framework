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
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proves the actual point of issue #61 — not just that a retry respects its flow's concurrency
 * budget (see {@link PipeliteRetryInheritsFlowConcurrencyIntegrationTest}), but that independent
 * retries can genuinely run <em>at the same time</em> instead of one at a time on {@code
 * RetryService}'s own single thread. {@code DispatchStrategy#dispatch} is asynchronous for a
 * {@code PooledDispatchStrategy}-backed flow specifically so this is possible — see
 * {@code 2026-Q3-pipelite-retry-concurrency-design.md} in the pipelite-framework-analysis
 * repository for why a synchronous, wait-for-completion implementation was tried first and
 * rejected (it only fixed budget accounting, not this).
 */
public class PipeliteRetryRunsConcurrentlyIntegrationTest {

    @Test
    public void givenTwoIndependentPendingRetries_thenBothRunConcurrentlyNotOneAtATime() throws InterruptedException {

        final ConfigurablePipeliteContext pipeliteContext = (ConfigurablePipeliteContext) Pipelite.createContext();
        // Timing/concurrency mechanics only, not persistence - in-memory keeps this hermetic.
        pipeliteContext.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());

        final Set<String> failedOnce = ConcurrentHashMap.newKeySet();
        final CountDownLatch bothRetriesRunning = new CountDownLatch(2);
        final CountDownLatch releaseRetries = new CountDownLatch(1);
        final AtomicInteger successCount = new AtomicInteger(0);

        final FlowDefinition testFlow = Pipelite.defineFlow("retry-parallelism-flow")
            .fromSource("retry-parallelism-in?concurrency=3")
            .process("handle", (io, c) -> {
                final String payload = io.getInputPayloadAs(String.class);
                if (failedOnce.add(payload)) {
                    throw new RuntimeException("simulated first-attempt failure for " + payload);
                }
                // The resumed retry attempt: signals it has started, then blocks until the test
                // releases it. If retries were still serialized on RetryService's own thread
                // (the pre-fix, synchronous-dispatch behavior), the second retry could never
                // reach this line until the first one's call returns - but the first one is
                // deliberately waiting right here for BOTH to arrive first, so a serialized
                // implementation would deadlock this test until bothRetriesRunning.await(...)
                // times out below.
                bothRetriesRunning.countDown();
                try {
                    if (!releaseRetries.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test did not release retries in time");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                successCount.incrementAndGet();
            })
            .toSink("retry-parallelism-out")
            .withRetryChannel(retry -> retry.maxAttempts(5))
            .build();

        pipeliteContext.registerFlowDefinition(testFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("retry-parallelism-in", exchangeFactory.createExchange("message-A"));
        pipeliteContext.supplyExchange("retry-parallelism-in", exchangeFactory.createExchange("message-B"));

        Assert.assertTrue(
            "both independent retries should be running concurrently within a few seconds - " +
                "if they were still serialized on RetryService's own thread, this would time out",
            bothRetriesRunning.await(10, TimeUnit.SECONDS));

        releaseRetries.countDown();
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> successCount.get() == 2);
    }

}
