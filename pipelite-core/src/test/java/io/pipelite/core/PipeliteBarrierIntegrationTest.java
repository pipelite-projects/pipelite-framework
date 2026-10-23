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
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.flow.barrier.BarrierCoordinator;
import io.pipelite.core.flow.barrier.BarrierFailedException;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.definition.SourceConcurrencyConfigurer;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Issue #150: an exchange of a flow waits at a barrier until the flows it awaits have completed, and then goes on
 * on the workers of its own flow. The completion of the awaited flows is told to the coordinator by hand here;
 * the flows tell it themselves when they run.
 */
public class PipeliteBarrierIntegrationTest {

    private PipeliteContext context;
    private final List<String> resumedOn = new CopyOnWriteArrayList<>();
    private final List<String> resumedPayloads = new CopyOnWriteArrayList<>();
    private final List<String> resumedHeaders = new CopyOnWriteArrayList<>();
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();

    // The barriers are on Pipelite Home, which outlives a test run: a key that a previous run already used would
    // meet its state, so every run uses its own.
    private final String run = UUID.randomUUID().toString().substring(0, 8);

    @Before
    public void setup() {
        context = Pipelite.createContext();
    }

    @After
    public void tearDown() {
        context.stop();
    }

    private BarrierCoordinator coordinator() {
        return ((InternalPipeliteContext) context).getBarrierCoordinator();
    }

    private String key(String suffix) {
        return run + "-" + suffix;
    }

    private void registerFlow(boolean concurrent) {
        final var source = concurrent
            ? Pipelite.defineFlow("ship-order").fromSource("queue://a-in", (SourceConcurrencyConfigurer c) -> c.concurrency(4))
            : Pipelite.defineFlow("ship-order").fromSource("queue://a-in");
        context.registerFlowDefinition(source
            .barrier("preparation", b -> b
                .correlateBy(c -> c.headerName("order-id"))
                .await("queue://step-b")
                .await("queue://step-c")
                .timeout(Duration.ofMinutes(10)))
            .process("next", (exchange, contribution) -> {
                resumedOn.add(Thread.currentThread().getName());
                resumedPayloads.add(exchange.getInputPayloadAs(String.class));
                resumedHeaders.add(exchange.tryGetHeader("order-id").orElse("none"));
            })
            .withExceptionHandler((exception, exchange) -> failures.add(exception))
            .build());
        context.start();
    }

    private void supply(String orderId, String payload) {
        final Exchange exchange = context.createExchange(payload);
        exchange.putHeader("order-id", orderId);
        context.supplyExchange("queue://a-in", exchange);
    }

    private void assertRanOnAWorkerOfTheFlow(String threadName) {
        Assert.assertNotEquals("not on the thread that told the completion", Thread.currentThread().getName(), threadName);
        Assert.assertTrue(threadName, threadName.startsWith("pipelite-"));
    }

    @Test
    public void givenTheAwaitedFlowsCompleteLater_thenTheExchangeResumesOnTheWorkersOfItsOwnFlow() throws Exception {
        registerFlow(false);
        supply(key("o-1"), "payload-1");
        Thread.sleep(300);
        Assert.assertTrue("it waits", resumedPayloads.isEmpty());

        coordinator().flowCompleted("step-b", key("o-1"));
        Thread.sleep(300);
        Assert.assertTrue("one of two flows is not enough", resumedPayloads.isEmpty());
        coordinator().flowCompleted("step-c", key("o-1"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> resumedPayloads.size() == 1);
        Assert.assertEquals("payload-1", resumedPayloads.get(0));
        Assert.assertEquals("what travelled with it is still there", key("o-1"), resumedHeaders.get(0));
        assertRanOnAWorkerOfTheFlow(resumedOn.get(0));
        Assert.assertTrue(failures.isEmpty());
    }

    @Test
    public void givenTheFlowHasConcurrency_thenTheExchangeResumesOnTheSharedWorkerPoolItRunsOn() {
        registerFlow(true);
        supply(key("o-1"), "payload-1");

        coordinator().flowCompleted("step-b", key("o-1"));
        coordinator().flowCompleted("step-c", key("o-1"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> resumedPayloads.size() == 1);
        assertRanOnAWorkerOfTheFlow(resumedOn.get(0));
        Assert.assertTrue(resumedOn.get(0), resumedOn.get(0).startsWith("pipelite-pool-"));
    }

    @Test
    public void givenTheAwaitedFlowsHadAlreadyCompleted_thenTheExchangeGoesOnAtOnce() {
        registerFlow(false);
        coordinator().flowCompleted("step-b", key("o-1"));
        coordinator().flowCompleted("step-c", key("o-1"));

        supply(key("o-1"), "payload-1");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> resumedPayloads.size() == 1);
        assertRanOnAWorkerOfTheFlow(resumedOn.get(0));
    }

    @Test
    public void givenSeveralExchangesWaitForTheSameKey_thenAllOfThemResumeAndTheOthersDoNot() throws Exception {
        registerFlow(true);
        supply(key("o-1"), "first");
        supply(key("o-1"), "second");
        supply(key("o-2"), "other-order");

        coordinator().flowCompleted("step-b", key("o-1"));
        coordinator().flowCompleted("step-c", key("o-1"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> resumedPayloads.size() == 2);
        Thread.sleep(300);
        Assert.assertEquals(2, resumedPayloads.size());
        Assert.assertTrue(resumedPayloads.containsAll(List.of("first", "second")));
    }

    @Test
    public void givenAnAwaitedFlowFails_thenTheErrorHandlingOfTheWaitingFlowGetsItAndNothingGoesOn() throws Exception {
        registerFlow(false);
        supply(key("o-1"), "payload-1");
        Thread.sleep(300);

        coordinator().flowFailed("step-b", key("o-1"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> failures.size() == 1);
        Assert.assertTrue(failures.get(0) instanceof BarrierFailedException);
        Assert.assertTrue(failures.get(0).getMessage(), failures.get(0).getMessage().contains("queue://step-b"));
        coordinator().flowCompleted("step-c", key("o-1"));
        Thread.sleep(300);
        Assert.assertTrue("a failed wait is not released afterwards", resumedPayloads.isEmpty());
    }

    @Test
    public void givenAnExchangeWithoutTheKey_thenItFailsThroughTheErrorHandlingAndWaitsForNothing() {
        registerFlow(false);
        context.supplyExchange("queue://a-in", context.createExchange("no header"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> failures.size() == 1);
        Assert.assertTrue(failures.get(0).getMessage(), failures.get(0).getMessage().contains("order-id"));
    }

    @Test
    public void givenAwaitsWithDifferentKeys_thenEachIsMatchedWithItsOwn() throws Exception {
        context.registerFlowDefinition(Pipelite.defineFlow("ship-order")
            .fromSource("queue://a-in")
            .barrier("preparation", b -> b
                .await("queue://step-b", c -> c.headerName("order-id"))
                .await("queue://step-c", c -> c.expression("Headers['customer']"))
                .timeout(Duration.ofMinutes(10)))
            .process("next", (exchange, contribution) -> resumedPayloads.add(exchange.getInputPayloadAs(String.class)))
            .build());
        context.start();
        final Exchange exchange = context.createExchange("payload-1");
        exchange.putHeader("order-id", key("o-1"));
        exchange.putHeader("customer", key("c-7"));
        context.supplyExchange("queue://a-in", exchange);

        coordinator().flowCompleted("step-b", key("o-1"));
        coordinator().flowCompleted("step-c", key("o-1"));   // the key of that flow is the customer, not the order
        Thread.sleep(300);
        Assert.assertTrue(resumedPayloads.isEmpty());

        coordinator().flowCompleted("step-c", key("c-7"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> resumedPayloads.size() == 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenABarrierThatCouldNeverWork_thenTheFlowIsRefusedWhenItIsDefined() {
        Pipelite.defineFlow("ship-order")
            .fromSource("queue://a-in")
            .barrier("incomplete", b -> b.correlateBy(c -> c.headerName("order-id")));
    }
}
