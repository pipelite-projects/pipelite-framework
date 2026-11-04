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
import io.pipelite.core.flow.barrier.BarrierInMemoryRepository;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.definition.SourceConcurrencyConfigurer;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Issue #156: the exchanges of a queue source that have the same key go through the flow one at a time, in the order
 * they arrived, while the ones with different keys run in parallel within the concurrency of the flow.
 */
public class PipeliteOrderedConcurrencyIntegrationTest {

    private static final int CONCURRENCY = 4;

    private DefaultPipeliteContext context;
    /** What the flow processed, as {@code customer:payload}, in the order it finished. */
    private final List<String> processed = new CopyOnWriteArrayList<>();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger maxRunning = new AtomicInteger();
    /** The payloads that are inside the flow now, by customer: more than one means two of a key ran together. */
    private final Map<String, AtomicInteger> runningByCustomer = new ConcurrentHashMap<>();
    private final List<String> overlaps = new CopyOnWriteArrayList<>();
    private final CountDownLatch releaseFirstOfA = new CountDownLatch(1);
    private final AtomicInteger started = new AtomicInteger();

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
        context.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());
        context.setBarrierRepository(new BarrierInMemoryRepository());
    }

    @After
    public void tearDown() {
        releaseFirstOfA.countDown();
        context.stop();
    }

    /**
     * A flow that records what it sees. The payload {@code A1} blocks until the test lets it go, so that what happens
     * to the exchanges behind it can be seen while it runs.
     */
    private void registerFlow(boolean ordered, int concurrency) {
        context.registerFlowDefinition(Pipelite.defineFlow("customer-updates")
            .fromSource("queue://customer-updates", (SourceConcurrencyConfigurer c) -> {
                c.durableInbox(false);
                final SourceConcurrencyConfigurer configured = c.concurrency(concurrency);
                if (ordered) {
                    configured.orderedBy(k -> k.headerName("customer-id"));
                }
            })
            .process("update", (exchange, contribution) -> {
                final String customer = exchange.tryGetHeader("customer-id").orElse("none");
                final String payload = exchange.getInputPayloadAs(String.class);
                final int now = running.incrementAndGet();
                maxRunning.accumulateAndGet(now, Math::max);
                if (runningByCustomer.computeIfAbsent(customer, key -> new AtomicInteger()).incrementAndGet() > 1) {
                    overlaps.add(customer + ":" + payload);
                }
                started.incrementAndGet();
                try {
                    if ("A1".equals(payload)) {
                        releaseFirstOfA.await(30, TimeUnit.SECONDS);
                    } else {
                        Thread.sleep(2 + Math.abs(payload.hashCode()) % 8);
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    runningByCustomer.get(customer).decrementAndGet();
                    running.decrementAndGet();
                    processed.add(customer + ":" + payload);
                }
            })
            .build());
        context.start();
    }

    private void supply(String customer, String payload) {
        final Exchange exchange = context.createExchange(payload);
        if (customer != null) {
            exchange.putHeader("customer-id", customer);
        }
        context.supplyExchange("queue://customer-updates", exchange);
    }

    private List<String> processedOf(String customer) {
        final List<String> result = new ArrayList<>();
        for (String entry : processed) {
            if (entry.startsWith(customer + ":")) {
                result.add(entry.substring(customer.length() + 1));
            }
        }
        return result;
    }

    @Test
    public void givenOrderedBy_thenTheExchangesOfAKeyRunInSequenceAndDifferentKeysOverlap() {
        registerFlow(true, CONCURRENCY);
        releaseFirstOfA.countDown();
        final List<String> customers = List.of("A", "B", "C", "D", "E", "F");
        final int perCustomer = 12;

        // interleaved, the way messages of several customers arrive
        for (int sequence = 1; sequence <= perCustomer; sequence++) {
            for (String customer : customers) {
                supply(customer, customer + sequence);
            }
        }

        Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() -> processed.size() == customers.size() * perCustomer);
        for (String customer : customers) {
            final List<String> expected = new ArrayList<>();
            for (int sequence = 1; sequence <= perCustomer; sequence++) {
                expected.add(customer + sequence);
            }
            Assert.assertEquals("the exchanges of " + customer + " in the order they arrived", expected, processedOf(customer));
        }
        Assert.assertTrue("two exchanges of a key ran together: " + overlaps, overlaps.isEmpty());
        Assert.assertTrue("different keys ran in parallel", maxRunning.get() > 1);
        Assert.assertTrue("never more than the concurrency of the flow: " + maxRunning.get(), maxRunning.get() <= CONCURRENCY);
    }

    @Test
    public void givenAnExchangeOfAKeyIsRunning_thenTheNextOfThatKeyWaitsAndTheOtherKeysDoNot() {
        registerFlow(true, CONCURRENCY);
        supply("A", "A1");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> started.get() == 1);

        supply("A", "A2");
        supply("B", "B1");
        supply("C", "C1");
        supply("A", "A3");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processed.size() == 2);
        Assert.assertEquals("only the other keys went through", Collections.emptyList(), processedOf("A"));
        Assert.assertEquals(List.of("B1"), processedOf("B"));
        Assert.assertEquals(List.of("C1"), processedOf("C"));

        releaseFirstOfA.countDown();

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processedOf("A").size() == 3);
        Assert.assertEquals(List.of("A1", "A2", "A3"), processedOf("A"));
        Assert.assertTrue(overlaps.isEmpty());
    }

    @Test
    public void givenNoOrdering_thenTheNextExchangeOfTheKeyRunsWhileTheFirstIsStillRunning() {
        registerFlow(false, CONCURRENCY);
        supply("A", "A1");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> started.get() == 1);

        supply("A", "A2");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processedOf("A").contains("A2"));
        Assert.assertFalse("A1 is still running: the second overtook it, which is what ordering prevents",
            processedOf("A").contains("A1"));
    }

    @Test
    public void givenAnExchangeWithoutTheKey_thenItIsProcessedWithoutOrdering() {
        registerFlow(true, CONCURRENCY);
        supply("A", "A1");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> started.get() == 1);

        supply(null, "no-key-1");
        supply(null, "no-key-2");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processedOf("none").size() == 2);
        Assert.assertEquals("A1 still runs, the exchanges without a key did not wait for anything", Collections.emptyList(), processedOf("A"));
    }

    @Test
    public void givenNoConcurrency_thenTheOrderingIsIgnoredAndTheFlowStillWorks() {
        registerFlow(true, 1);
        releaseFirstOfA.countDown();

        for (int sequence = 1; sequence <= 5; sequence++) {
            supply("A", "A" + sequence);
        }

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> processed.size() == 5);
        Assert.assertEquals(List.of("A1", "A2", "A3", "A4", "A5"), processedOf("A"));
        Assert.assertEquals(1, maxRunning.get());
    }

    @Test
    public void givenManyExchangesWaitBehindAKey_thenTheSourceIsToldToSlowDown() throws Exception {
        registerFlow(true, 2);
        supply("A", "A1");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> started.get() == 1);

        final int total = 300;
        final AtomicInteger supplied = new AtomicInteger();
        final Thread producer = new Thread(() -> {
            for (int sequence = 2; sequence <= total; sequence++) {
                supply("A", "A" + sequence);
                supplied.incrementAndGet();
            }
        });
        producer.start();

        // The dispatcher keeps the exchanges behind the key: if they did not count as waiting, it would empty the
        // queue into memory and the producer would never be held.
        Thread.sleep(1500);
        final int suppliedWhileBlocked = supplied.get();
        Assert.assertTrue("the producer was not held back: " + suppliedWhileBlocked, suppliedWhileBlocked < total - 1);
        Assert.assertTrue("and it went on until the source was full: " + suppliedWhileBlocked, suppliedWhileBlocked > 5);

        releaseFirstOfA.countDown();

        producer.join(60_000);
        Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() -> processed.size() == total);
        final List<String> expected = new ArrayList<>();
        for (int sequence = 1; sequence <= total; sequence++) {
            expected.add("A" + sequence);
        }
        Assert.assertEquals(expected, processedOf("A"));
        Assert.assertTrue(overlaps.isEmpty());
    }
}
