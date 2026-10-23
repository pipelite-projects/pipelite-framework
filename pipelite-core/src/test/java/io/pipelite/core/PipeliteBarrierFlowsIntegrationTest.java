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

import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.context.ContextValidationException;
import io.pipelite.core.flow.barrier.BarrierFailedException;
import io.pipelite.core.flow.barrier.BarrierInMemoryRepository;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.definition.SourceConcurrencyConfigurer;
import io.pipelite.dsl.process.Processor;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Issue #150, the use case it was written for, with real flows: the exchange X of the flow A waits for the exchange
 * Y of the flow B and the exchange Z of the flow C, and goes on when both have completed, on the workers of A.
 */
public class PipeliteBarrierFlowsIntegrationTest {

    private static final Processor DOES_NOTHING = (exchange, contribution) -> { };

    private DefaultPipeliteContext context;
    private final List<String> shipped = new CopyOnWriteArrayList<>();
    private final List<String> shippedOn = new CopyOnWriteArrayList<>();
    private final List<Throwable> failuresOfA = new CopyOnWriteArrayList<>();
    private final List<Throwable> failuresOfB = new CopyOnWriteArrayList<>();
    private final AtomicReference<Processor> behaviorOfB = new AtomicReference<>(DOES_NOTHING);
    private final List<String> processedByB = new CopyOnWriteArrayList<>();

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
        // in memory: no state on disk, and nothing left from another run
        context.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());
        context.setBarrierRepository(new BarrierInMemoryRepository());
    }

    @After
    public void tearDown() {
        context.stop();
    }

    private void registerA(boolean concurrent) {
        final var source = concurrent
            ? Pipelite.defineFlow("ship-order").fromSource("queue://a-in", (SourceConcurrencyConfigurer c) -> c.concurrency(4))
            : Pipelite.defineFlow("ship-order").fromSource("queue://a-in");
        context.registerFlowDefinition(source
            .barrier("preparation", b -> b
                .correlateBy(c -> c.headerName("order-id"))
                .await("queue://step-b")
                .await("queue://step-c")
                .timeout(Duration.ofMinutes(10)))
            .process("ship", (exchange, contribution) -> {
                shippedOn.add(Thread.currentThread().getName());
                shipped.add(exchange.getInputPayloadAs(String.class));
            })
            .withExceptionHandler((exception, exchange) -> failuresOfA.add(exception))
            .build());
    }

    /** The flow B does what the test asks of it, with an exception handler of its own, so a failure is a handled one. */
    private void registerB() {
        context.registerFlowDefinition(Pipelite.defineFlow("step-b")
            .fromSource("queue://step-b")
            .process("do-b", (exchange, contribution) -> {
                processedByB.add(exchange.getInputPayloadAs(String.class));
                behaviorOfB.get().process(exchange, contribution);
            })
            .withExceptionHandler((exception, exchange) -> failuresOfB.add(exception))
            .build());
    }

    private void registerC() {
        context.registerFlowDefinition(Pipelite.defineFlow("step-c")
            .fromSource("queue://step-c")
            .process("do-c", DOES_NOTHING)
            .build());
    }

    private void supply(String queue, String orderId, String payload) {
        final Exchange exchange = context.createExchange(payload);
        if (orderId != null) {
            exchange.putHeader("order-id", orderId);
        }
        context.supplyExchange("queue://" + queue, exchange);
    }

    private void assertShippedOnAWorkerOfA(int index) {
        Assert.assertNotEquals(Thread.currentThread().getName(), shippedOn.get(index));
        Assert.assertTrue(shippedOn.get(index), shippedOn.get(index).startsWith("pipelite-"));
    }

    @Test
    public void givenXWaitsForYAndZ_thenItGoesOnOnlyAfterBothHaveCompleted() throws Exception {
        registerA(false);
        registerB();
        registerC();
        context.start();

        supply("a-in", "o-1", "X");
        Thread.sleep(400);
        Assert.assertTrue("X waits", shipped.isEmpty());

        supply("step-b", "o-1", "Y");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processedByB.size() == 1);
        Thread.sleep(400);
        Assert.assertTrue("Y alone is not enough", shipped.isEmpty());

        supply("step-c", "o-1", "Z");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> shipped.size() == 1);
        Assert.assertEquals("X", shipped.get(0));
        assertShippedOnAWorkerOfA(0);
        Assert.assertTrue(failuresOfA.isEmpty());
    }

    @Test
    public void givenYAndZHadAlreadyCompleted_thenXGoesOnAtOnce() throws Exception {
        registerA(false);
        registerB();
        registerC();
        context.start();
        supply("step-b", "o-1", "Y");
        supply("step-c", "o-1", "Z");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processedByB.size() == 1);
        Thread.sleep(500);

        supply("a-in", "o-1", "X");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> shipped.size() == 1);
        assertShippedOnAWorkerOfA(0);
    }

    @Test
    public void givenYIsDroppedByAFilter_thenItCountsAsCompleted() throws Exception {
        registerA(false);
        context.registerFlowDefinition(Pipelite.defineFlow("step-b")
            .fromSource("queue://step-b")
            .filter("only-the-wanted", "Headers['wanted'] == 'yes'")
            .process("do-b", DOES_NOTHING)
            .build());
        registerC();
        context.start();
        supply("a-in", "o-1", "X");
        supply("step-c", "o-1", "Z");
        Thread.sleep(400);
        Assert.assertTrue(shipped.isEmpty());

        supply("step-b", "o-1", "Y");   // no 'wanted' header: the filter drops it

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> shipped.size() == 1);
    }

    @Test
    public void givenYFailsAndItsErrorHandlingTakesIt_thenXFailsAtOnceWithoutWaitingForZ() throws Exception {
        registerA(false);
        registerB();
        registerC();
        behaviorOfB.set((exchange, contribution) -> {
            throw new IllegalStateException("Y cannot be done");
        });
        context.start();
        supply("a-in", "o-1", "X");

        supply("step-b", "o-1", "Y");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> failuresOfA.size() == 1);
        Assert.assertTrue(failuresOfA.get(0) instanceof BarrierFailedException);
        Assert.assertTrue(failuresOfA.get(0).getMessage(), failuresOfA.get(0).getMessage().contains("queue://step-b"));
        Assert.assertEquals("the failure of Y reached the handler of B, not the one of A", 1, failuresOfB.size());
        supply("step-c", "o-1", "Z");
        Thread.sleep(400);
        Assert.assertTrue("a failed wait is not released afterwards", shipped.isEmpty());
    }

    @Test
    public void givenYFailsThenSucceedsOnRetry_thenXKeepsWaitingAndGoesOnAfterTheRetry() throws Exception {
        registerA(false);
        context.registerFlowDefinition(Pipelite.defineFlow("step-b")
            .fromSource("queue://step-b")
            .process("do-b", (exchange, contribution) -> {
                if (processedByB.add(exchange.getInputPayloadAs(String.class)) && processedByB.size() == 1) {
                    throw new IllegalStateException("the first attempt fails");
                }
            })
            .withRetry(retry -> retry.maxAttempts(5).onErrorChannel(err -> err.toDLQ()))
            .build());
        registerC();
        context.start();
        supply("a-in", "o-1", "X");
        supply("step-c", "o-1", "Z");

        supply("step-b", "o-1", "Y");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processedByB.size() == 1);
        Thread.sleep(300);
        Assert.assertTrue("a failure that is being retried is not a completion", shipped.isEmpty());
        Assert.assertTrue("nor a failure", failuresOfA.isEmpty());

        // the retry runs the same step again: it succeeds this time, and then X goes on
        Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() -> shipped.size() == 1);
        Assert.assertTrue(failuresOfA.isEmpty());
    }

    @Test
    public void givenYNeverSucceedsAndTheRetriesAreExhausted_thenXFails() {
        registerA(false);
        final AtomicInteger attempts = new AtomicInteger();
        context.registerFlowDefinition(Pipelite.defineFlow("step-b")
            .fromSource("queue://step-b")
            .process("do-b", (exchange, contribution) -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("always fails");
            })
            .withRetry(retry -> retry.maxAttempts(2).onErrorChannel(err -> err.toDLQ()))
            .build());
        registerC();
        context.start();
        supply("a-in", "o-1", "X");
        supply("step-c", "o-1", "Z");

        supply("step-b", "o-1", "Y");

        Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() -> failuresOfA.size() == 1);
        Assert.assertTrue(failuresOfA.get(0) instanceof BarrierFailedException);
        Assert.assertTrue("it was retried before it was given up", attempts.get() >= 2);
        Assert.assertTrue(shipped.isEmpty());
    }

    @Test
    public void givenTheKeyCannotBeReadFromY_thenYIsNotAffectedAndXKeepsWaiting() throws Exception {
        registerA(false);
        registerB();
        registerC();
        context.start();
        supply("a-in", "o-1", "X");
        supply("step-c", "o-1", "Z");

        supply("step-b", null, "Y");   // no order-id header

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processedByB.size() == 1);
        Thread.sleep(500);
        Assert.assertTrue("B did its work as always", failuresOfB.isEmpty());
        Assert.assertTrue("A waits for what it cannot see", shipped.isEmpty());
        Assert.assertTrue(failuresOfA.isEmpty());
    }

    @Test
    public void givenAFlowWithConcurrency_thenEveryOrderGoesOnOnceOnTheSharedPool() throws Exception {
        registerA(true);
        registerB();
        registerC();
        context.start();
        final int orders = 20;

        for (int i = 0; i < orders; i++) {
            supply("a-in", "o-" + i, "X-" + i);
        }
        for (int i = 0; i < orders; i++) {
            supply("step-b", "o-" + i, "Y-" + i);
            supply("step-c", "o-" + i, "Z-" + i);
        }

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> shipped.size() == orders);
        Thread.sleep(500);
        Assert.assertEquals("once each", orders, shipped.size());
        Assert.assertEquals(orders, shipped.stream().distinct().count());
        for (int i = 0; i < orders; i++) {
            assertShippedOnAWorkerOfA(i);
            Assert.assertTrue(shippedOn.get(i), shippedOn.get(i).startsWith("pipelite-pool-"));
        }
        Assert.assertTrue(failuresOfA.isEmpty());
    }

    @Test
    public void givenAFlowThatAwaitsAFlowThatAwaitsAnother_thenTheCompletionsReachTheFirstInTurn() throws Exception {
        final List<String> bResumed = new CopyOnWriteArrayList<>();
        context.registerFlowDefinition(Pipelite.defineFlow("ship-order")
            .fromSource("queue://a-in")
            .barrier("after-b", b -> b.correlateBy(c -> c.headerName("order-id")).await("queue://step-b").timeout(Duration.ofMinutes(10)))
            .process("ship", (exchange, contribution) -> shipped.add(exchange.getInputPayloadAs(String.class)))
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("step-b")
            .fromSource("queue://step-b")
            .barrier("after-c", b -> b.correlateBy(c -> c.headerName("order-id")).await("queue://step-c").timeout(Duration.ofMinutes(10)))
            .process("do-b", (exchange, contribution) -> bResumed.add(exchange.getInputPayloadAs(String.class)))
            .build());
        registerC();
        context.start();
        supply("a-in", "o-1", "X");
        supply("step-b", "o-1", "Y");
        Thread.sleep(500);
        Assert.assertTrue(bResumed.isEmpty());
        Assert.assertTrue(shipped.isEmpty());

        supply("step-c", "o-1", "Z");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> bResumed.size() == 1);
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> shipped.size() == 1);
    }

    @Test
    public void givenABarrierThatAwaitsAFlowThatDoesNotExist_thenTheContextDoesNotStart() {
        registerA(false);
        registerB();   // and no flow for step-c

        try {
            context.start();
            Assert.fail("the context must not start with a barrier that awaits nothing");
        } catch (ContextValidationException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("step-c"));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("barrier 'preparation'"));
        }
    }
}
