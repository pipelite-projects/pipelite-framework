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
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * End-to-end coverage of {@code fromSource} concurrency (issue #4): a plain, non-concurrent
 * ingress flow ({@code fromSource("origin-...")}) linked via {@code toSink("link://...")} to a
 * second flow whose {@code fromSource} declares {@code concurrency > 1} — exactly the two-flow
 * pattern the requirements doc settled on, mirroring {@link PipeliteFlowLinkIntegrationTest} but
 * asserting genuine parallel execution instead of a single forwarded message.
 */
public class PipeliteSourceConcurrencyIntegrationTest {

    private PipeliteContext context;

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
    }

    @After
    public void tearDown() {
        context.stop();
    }

    @Test
    public void shouldProcessConcurrentlyWhenConcurrencyConfiguredOnLinkedFlow() {

        final int numOfMessages = 20;
        final int concurrency = 4;

        final Set<Long> observedThreadIds = ConcurrentHashMap.newKeySet();
        final AtomicInteger inFlight = new AtomicInteger(0);
        final AtomicInteger maxObservedInFlight = new AtomicInteger(0);
        final AtomicInteger receivedCount = new AtomicInteger(0);

        final FlowDefinition origin = Pipelite.defineFlow("origin-flow-concurrency")
            .fromSource("origin-start-concurrency")
            .toSink("link://destination-start-concurrency")
            .build();

        final FlowDefinition destination = Pipelite.defineFlow("destination-flow-concurrency")
            .fromSource("destination-start-concurrency?concurrency=" + concurrency)
            .process("process-message", (ioContext, contribution) -> {
                observedThreadIds.add(Thread.currentThread().getId());
                final int current = inFlight.incrementAndGet();
                maxObservedInFlight.updateAndGet(previousMax -> Math.max(previousMax, current));
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    inFlight.decrementAndGet();
                    receivedCount.incrementAndGet();
                }
            })
            .build();

        context.registerFlowDefinition(origin);
        context.registerFlowDefinition(destination);
        context.start();

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();
        for (int i = 0; i < numOfMessages; i++) {
            context.supplyExchange("origin-start-concurrency", exchangeFactory.createExchange("message-" + i));
        }

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> receivedCount.get() == numOfMessages);

        Assert.assertTrue("expected more than one distinct worker thread across the shared pool",
            observedThreadIds.size() > 1);
        Assert.assertTrue("expected genuine overlap (max in-flight > 1)", maxObservedInFlight.get() > 1);
        Assert.assertTrue("concurrency=" + concurrency + " must never be exceeded, observed max in-flight " + maxObservedInFlight.get(),
            maxObservedInFlight.get() <= concurrency);
    }

    @Test
    public void shouldPreserveSequentialSingleThreadBehaviorWhenConcurrencyNotConfigured() {

        final int numOfMessages = 5;
        final Set<Long> observedThreadIds = ConcurrentHashMap.newKeySet();
        final AtomicInteger receivedCount = new AtomicInteger(0);

        final FlowDefinition origin = Pipelite.defineFlow("origin-flow-sequential")
            .fromSource("origin-start-sequential")
            .toSink("link://destination-start-sequential")
            .build();

        final FlowDefinition destination = Pipelite.defineFlow("destination-flow-sequential")
            .fromSource("destination-start-sequential") // no concurrency param — today's exact behavior
            .process("process-message", (ioContext, contribution) -> {
                observedThreadIds.add(Thread.currentThread().getId());
                receivedCount.incrementAndGet();
            })
            .build();

        context.registerFlowDefinition(origin);
        context.registerFlowDefinition(destination);
        context.start();

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();
        for (int i = 0; i < numOfMessages; i++) {
            context.supplyExchange("origin-start-sequential", exchangeFactory.createExchange("message-" + i));
        }

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> receivedCount.get() == numOfMessages);

        Assert.assertEquals("a single dedicated thread must process every message when concurrency is unset",
            1, observedThreadIds.size());
    }

    @Test
    public void shouldKeepConcurrencyOneFlowUnaffectedWhenSharedPoolIsSaturated() {

        final int poolSize = 2;
        ((ConfigurablePipeliteContext) context).setMaxSourceWorkerPoolSize(poolSize);

        final CountDownLatch saturatingTasksStarted = new CountDownLatch(poolSize);
        final CountDownLatch releaseSaturatingTasks = new CountDownLatch(1);

        // occupies every thread in the shared pool for the duration of the test
        final FlowDefinition saturating = Pipelite.defineFlow("saturating-flow")
            .fromSource("saturating-start?concurrency=" + poolSize)
            .process("occupy-pool", (ioContext, contribution) -> {
                saturatingTasksStarted.countDown();
                try {
                    releaseSaturatingTasks.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            })
            .build();

        final AtomicInteger sequentialReceivedCount = new AtomicInteger(0);
        // concurrency left unset: processes inline on its own dedicated thread, never touches
        // the shared pool the saturating flow is monopolizing
        final FlowDefinition sequential = Pipelite.defineFlow("sequential-flow")
            .fromSource("sequential-start")
            .process("quick-process", (ioContext, contribution) -> sequentialReceivedCount.incrementAndGet())
            .build();

        context.registerFlowDefinition(saturating);
        context.registerFlowDefinition(sequential);
        context.start();

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();
        try {
            for (int i = 0; i < poolSize; i++) {
                context.supplyExchange("saturating-start", exchangeFactory.createExchange("saturate-" + i));
            }
            Assert.assertTrue("expected the shared pool to be fully occupied by the saturating flow",
                saturatingTasksStarted.await(5, TimeUnit.SECONDS));

            final int numOfMessages = 5;
            for (int i = 0; i < numOfMessages; i++) {
                context.supplyExchange("sequential-start", exchangeFactory.createExchange("seq-" + i));
            }

            // if the sequential flow were queueing behind the saturated shared pool instead of
            // processing inline on its own thread, this would time out
            Awaitility.await().atMost(1, TimeUnit.SECONDS).until(() -> sequentialReceivedCount.get() == numOfMessages);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Assert.fail("interrupted while waiting for the saturating flow to occupy the shared pool");
        } finally {
            releaseSaturatingTasks.countDown();
        }
    }

    @Test
    public void shouldShareSourceWorkerPoolBudgetAcrossMultipleConcurrentFlows() {

        final int poolSize = 3;
        final int concurrencyPerFlow = 3;
        final int numFlows = 2;
        final int messagesPerFlow = 10;
        ((ConfigurablePipeliteContext) context).setMaxSourceWorkerPoolSize(poolSize);

        final Set<Long> observedThreadIds = ConcurrentHashMap.newKeySet();
        final AtomicInteger inFlight = new AtomicInteger(0);
        final AtomicInteger maxObservedInFlight = new AtomicInteger(0);
        final AtomicInteger receivedCount = new AtomicInteger(0);

        for (int f = 0; f < numFlows; f++) {
            final FlowDefinition flow = Pipelite.defineFlow("shared-pool-flow-" + f)
                .fromSource("shared-pool-start-" + f + "?concurrency=" + concurrencyPerFlow)
                .process("process-message", (ioContext, contribution) -> {
                    observedThreadIds.add(Thread.currentThread().getId());
                    final int current = inFlight.incrementAndGet();
                    maxObservedInFlight.updateAndGet(previousMax -> Math.max(previousMax, current));
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        inFlight.decrementAndGet();
                        receivedCount.incrementAndGet();
                    }
                })
                .build();
            context.registerFlowDefinition(flow);
        }
        context.start();

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();
        for (int f = 0; f < numFlows; f++) {
            for (int i = 0; i < messagesPerFlow; i++) {
                context.supplyExchange("shared-pool-start-" + f, exchangeFactory.createExchange("msg-" + f + "-" + i));
            }
        }

        Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> receivedCount.get() == messagesPerFlow * numFlows);

        Assert.assertTrue("expected genuine overlap across flows sharing the pool", maxObservedInFlight.get() > 1);
        Assert.assertTrue("the shared pool budget (" + poolSize + ") must bound total concurrent executions across ALL flows " +
                "even though each flow individually declared concurrency=" + concurrencyPerFlow + ", observed max in-flight " + maxObservedInFlight.get(),
            maxObservedInFlight.get() <= poolSize);
        Assert.assertTrue("distinct worker threads used across all flows must not exceed the shared pool size",
            observedThreadIds.size() <= poolSize);
    }

}
