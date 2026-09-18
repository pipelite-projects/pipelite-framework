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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regression coverage for issue #61's actual root cause (not the per-tick batching fix, which
 * addressed only its literal wording): a retry resumed via {@code SupplyExchangeProcessor}'s
 * direct-jump mechanism used to run on {@code RetryService}'s own thread, entirely outside the
 * originating flow's own {@code concurrency(n)} budget — see
 * {@code 2026-Q3-pipelite-retry-concurrency-design.md} in the pipelite-framework-analysis
 * repository. This test proves the fix concretely: with a {@code concurrency(2)} flow whose two
 * permits are both held by long-running "fresh" messages, a pending retry for that same flow must
 * wait for a permit exactly like a fresh message would, not run immediately for free.
 */
public class PipeliteRetryInheritsFlowConcurrencyIntegrationTest {

    @Test
    public void givenBothConcurrencyPermitsHeld_whenARetryIsPending_thenItWaitsForAPermitInsteadOfRunningForFree() throws InterruptedException {

        final ConfigurablePipeliteContext pipeliteContext = (ConfigurablePipeliteContext) Pipelite.createContext();
        // Timing/concurrency mechanics only, not persistence - in-memory keeps this hermetic.
        pipeliteContext.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());

        final CountDownLatch blockersReady = new CountDownLatch(2);
        final CountDownLatch releaseBlockers = new CountDownLatch(1);
        final AtomicInteger retryAttempt = new AtomicInteger(0);
        final AtomicBoolean retryStarted = new AtomicBoolean(false);
        final AtomicBoolean retryStartedWhilePermitsWereHeld = new AtomicBoolean(false);
        final CountDownLatch retryCompleted = new CountDownLatch(1);

        final FlowDefinition testFlow = Pipelite.defineFlow("retry-concurrency-flow")
            .fromSource("retry-concurrency-in?concurrency=2")
            .process("handle", (io, c) -> {
                final String payload = io.getInputPayloadAs(String.class);
                if (payload.startsWith("blocker")) {
                    blockersReady.countDown();
                    try {
                        if (!releaseBlockers.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("test did not release blockers in time");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return;
                }
                if (retryAttempt.incrementAndGet() == 1) {
                    throw new RuntimeException("simulated first-attempt failure, creates the dump");
                }
                // The resumed retry attempt.
                retryStarted.set(true);
                if (releaseBlockers.getCount() > 0) {
                    retryStartedWhilePermitsWereHeld.set(true);
                }
                retryCompleted.countDown();
            })
            .toSink("retry-concurrency-out")
            .withRetry(retry -> retry.maxAttempts(5).onErrorChannel(err -> err.toDLQ()))
            .build();

        pipeliteContext.registerFlowDefinition(testFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();

        // Phase 1: the retryable message's first attempt runs with both permits free, fails, and
        // creates a dump.
        pipeliteContext.supplyExchange("retry-concurrency-in", exchangeFactory.createExchange("retryable"));
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> retryAttempt.get() == 1);

        // Phase 2: saturate both of this flow's concurrency permits with long-running messages.
        pipeliteContext.supplyExchange("retry-concurrency-in", exchangeFactory.createExchange("blocker-1"));
        pipeliteContext.supplyExchange("retry-concurrency-in", exchangeFactory.createExchange("blocker-2"));
        Assert.assertTrue("both blockers should have started within 10s",
            blockersReady.await(10, TimeUnit.SECONDS));

        // Phase 3: give the retry-channel's poll loop (default period 1s) ample opportunity to
        // have tried to resume the pending retry while both permits are still held.
        Thread.sleep(2500);
        Assert.assertFalse("the retry must not have started while both concurrency permits were held",
            retryStarted.get());

        // Phase 4: free both permits; the retry should now proceed.
        releaseBlockers.countDown();
        Assert.assertTrue("the retry should complete shortly after a permit frees up",
            retryCompleted.await(10, TimeUnit.SECONDS));
        Assert.assertFalse("the retry must have waited for a free permit, not run for free",
            retryStartedWhilePermitsWereHeld.get());
    }

}
