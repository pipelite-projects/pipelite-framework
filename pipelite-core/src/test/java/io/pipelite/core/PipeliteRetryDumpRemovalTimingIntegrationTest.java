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
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.deadletter.DeadLetterQueueRepository;
import io.pipelite.core.flow.execution.deadletter.DeadLetteredExchange;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regression coverage for issue #58: a retry-channel dump must stay in the repository for the
 * whole duration of a retry attempt, not just while it's waiting to be picked up. Before the fix,
 * {@code ResolveExecutionDumpProcessor} removed the dump immediately upon resolving it — a crash
 * anywhere between that removal and the attempt's own outcome (success, or a new dump saved for a
 * further attempt) lost the message with no durable record of it at all, widening the crash
 * window #68 closed for "waiting to be retried" to also cover "actively being retried".
 */
public class PipeliteRetryDumpRemovalTimingIntegrationTest {

    /**
     * Wraps an in-memory repository, tracking which ids are currently saved - lets a test observe
     * whether a dump is still present at a given point without the production interface needing a
     * {@code size()}/{@code contains(id)} method of its own.
     */
    private static final class TrackingFlowExecutionDumpRepository implements FlowExecutionDumpRepository {

        private final FlowExecutionDumpRepository delegate = new FlowExecutionDumpInMemoryRepository();
        private final Set<String> pendingIds = ConcurrentHashMap.newKeySet();

        @Override
        public Optional<FlowExecutionDump> tryLoad(String id) {
            return delegate.tryLoad(id);
        }

        @Override
        public Optional<FlowExecutionDump> poll() {
            return delegate.poll();
        }

        @Override
        public void save(FlowExecutionDump flowExecutionDump) {
            delegate.save(flowExecutionDump);
            pendingIds.add(flowExecutionDump.getId());
        }

        @Override
        public void remove(String id) {
            delegate.remove(id);
            pendingIds.remove(id);
        }

        @Override
        public boolean tryClaim(String id) {
            return delegate.tryClaim(id);
        }

        int pendingCount() {
            return pendingIds.size();
        }
    }

    private ConfigurablePipeliteContext pipeliteContext;
    private TrackingFlowExecutionDumpRepository repository;

    @Before
    public void setup() {
        pipeliteContext = (ConfigurablePipeliteContext) Pipelite.createContext();
        repository = new TrackingFlowExecutionDumpRepository();
        pipeliteContext.setFlowExecutionDumpRepository(repository);
    }

    @Test
    public void givenARetryInProgress_whenTheResumedAttemptRuns_thenTheOriginalDumpIsStillInTheRepository() {

        final AtomicInteger invocationCount = new AtomicInteger(0);
        final AtomicInteger pendingCountDuringRetry = new AtomicInteger(-1);

        final FlowDefinition testFlow = Pipelite.defineFlow("dump-removal-timing-flow")
            .fromSource("dump-removal-timing-in")
            .process("maybe-fail", (io, c) -> {
                if (invocationCount.incrementAndGet() == 1) {
                    throw new RuntimeException("simulated failure - triggers the first dump");
                }
                // This is the resumed attempt, invoked synchronously by SupplyExchangeProcessor -
                // before the #58 fix, ResolveExecutionDumpProcessor would have already removed the
                // dump before this line ever ran.
                pendingCountDuringRetry.set(repository.pendingCount());
            })
            .toSink("dump-removal-timing-out")
            .withRetry(retry -> retry.maxAttempts(5).onErrorChannel(err -> err.toDLQ()))
            .build();

        pipeliteContext.registerFlowDefinition(testFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("dump-removal-timing-in", exchangeFactory.createExchange("test-message"));

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> invocationCount.get() >= 2);

        Assert.assertEquals("the original dump must still be present while its retry attempt is running",
            1, pendingCountDuringRetry.get());

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> repository.pendingCount() == 0);
    }

    /**
     * Issue #91 reshaped this scenario: retry can no longer be declared without an exhaustion
     * action, so "exhausted with no dead-letter channel" is no longer expressible. This now
     * exercises the built-in DLQ (issue #93) as that action instead - the original dump must
     * still be removed (not leaked) once its replacement lands in the DLQ.
     */
    @Test
    public void givenRetryAttemptsAreExhaustedWithBuiltInDlqConfigured_thenTheOriginalDumpIsRemovedAndTheEntryIsWrittenToTheDlq() {

        final AtomicInteger attemptCount = new AtomicInteger(0);
        final List<DeadLetteredExchange> dlqEntries = new CopyOnWriteArrayList<>();
        pipeliteContext.setDeadLetterQueueRepository(dlqEntries::add);

        final FlowDefinition testFlow = Pipelite.defineFlow("dump-removal-exhaustion-flow")
            .fromSource("dump-removal-exhaustion-in")
            .process("always-fail", (io, c) -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("simulated persistent failure");
            })
            .toSink("dump-removal-exhaustion-out")
            .withRetry(retry -> retry.maxAttempts(2).onErrorChannel(err -> err.toDLQ()))
            .build();

        pipeliteContext.registerFlowDefinition(testFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("dump-removal-exhaustion-in", exchangeFactory.createExchange("poison-payload"));

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> attemptCount.get() == 2);
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> repository.pendingCount() == 0);
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> dlqEntries.size() == 1);
        Assert.assertEquals("dump-removal-exhaustion-flow", dlqEntries.get(0).getFlowName());
    }

}
