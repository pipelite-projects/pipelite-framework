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
import io.pipelite.core.flow.execution.deadletter.DeadLetteredExchange;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.definition.SourceConcurrencyConfigurer;
import io.pipelite.dsl.process.Processor;
import io.pipelite.dsl.definition.builder.Backoff;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Issue #156, what happens to the order when an exchange fails: the exchanges behind it with the same key wait until
 * it has really been processed, after the retries that succeed, or given up, in the dead letter queue or by the
 * error handling, while the other keys go on.
 */
public class PipeliteOrderedConcurrencyRetryIntegrationTest {

    private DefaultPipeliteContext context;
    private final List<String> processed = new CopyOnWriteArrayList<>();
    private final List<DeadLetteredExchange> deadLetters = new CopyOnWriteArrayList<>();
    private final List<String> handled = new CopyOnWriteArrayList<>();
    /** What entered the flow and what was updated, in the order it happened. */
    private final List<String> trace = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
        context.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());
        context.setBarrierRepository(new BarrierInMemoryRepository());
        context.setDeadLetterQueueRepository(deadLetters::add);
    }

    @After
    public void tearDown() {
        context.stop();
    }

    /** Fails the first {@code failures} attempts of {@code failing}, and processes everything else. */
    private Processor processor(String failing, int failures) {
        return (exchange, contribution) -> {
            final String payload = exchange.getInputPayloadAs(String.class);
            final int attempt = attempts.computeIfAbsent(payload, key -> new AtomicInteger()).incrementAndGet();
            if (payload.equals(failing) && attempt <= failures) {
                throw new IllegalStateException("simulated failure of " + payload);
            }
            trace.add("update:" + payload);
            processed.add(payload);
        };
    }

    private void registerWithRetry(Processor processor, int maxAttempts) {
        context.registerFlowDefinition(Pipelite.defineFlow("customer-updates")
            .fromSource("queue://customer-updates", (SourceConcurrencyConfigurer c) -> {
                c.durableInbox(false);
                c.concurrency(4).orderedBy(k -> k.headerName("customer-id"));
            })
            .process("update", processor)
            .withRetry(retry -> retry
                .maxAttempts(maxAttempts)
                .backoff(Backoff.linear(Duration.ofSeconds(1)))
                .onErrorChannel(err -> err.toDLQ()))
            .build());
        context.start();
    }

    private void supply(String customer, String payload) {
        final Exchange exchange = context.createExchange(payload);
        exchange.putHeader("customer-id", customer);
        context.supplyExchange("queue://customer-updates", exchange);
    }

    private List<String> processedOf(String customer) {
        final List<String> result = new ArrayList<>();
        for (String payload : processed) {
            if (payload.startsWith(customer)) {
                result.add(payload);
            }
        }
        return result;
    }

    @Test
    public void givenTheFirstExchangeOfAKeyIsWaitingForItsRetry_thenTheNextOnesOfThatKeyWaitAndTheOtherKeysDoNot() {
        registerWithRetry(processor("A1", 1), 3);

        supply("A", "A1");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> attempts.containsKey("A1"));
        supply("A", "A2");
        supply("B", "B1");
        supply("A", "A3");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processed.contains("B1"));
        Assert.assertEquals("the key of A1 is held while A1 waits for its retry", Collections.emptyList(), processedOf("A"));

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> processedOf("A").size() == 3);
        Assert.assertEquals(List.of("A1", "A2", "A3"), processedOf("A"));
        Assert.assertEquals("A1 ran twice, the others once", 2, attempts.get("A1").get());
        Assert.assertEquals(1, attempts.get("A2").get());
        Assert.assertTrue(deadLetters.isEmpty());
    }

    @Test
    public void givenTheRetriesAreExhausted_thenTheKeyIsLetGoAndTheNextExchangeRuns() {
        registerWithRetry(processor("A1", Integer.MAX_VALUE), 2);

        supply("A", "A1");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> attempts.containsKey("A1"));
        supply("A", "A2");
        supply("B", "B1");
        supply("A", "A3");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processed.contains("B1"));
        Assert.assertEquals(Collections.emptyList(), processedOf("A"));

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> deadLetters.size() == 1);
        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> processedOf("A").size() == 2);
        Assert.assertEquals("what is left of the key goes on, in order", List.of("A2", "A3"), processedOf("A"));
        Assert.assertEquals(2, attempts.get("A1").get());
    }

    @Test
    public void givenTheErrorHandlingTakesTheFailure_thenTheKeyIsLetGoAtOnce() {
        context.registerFlowDefinition(Pipelite.defineFlow("customer-updates")
            .fromSource("queue://customer-updates", (SourceConcurrencyConfigurer c) -> {
                c.durableInbox(false);
                c.concurrency(4).orderedBy(k -> k.headerName("customer-id"));
            })
            .process("update", processor("A1", Integer.MAX_VALUE))
            .withExceptionHandler((exception, exchange) -> handled.add(exchange.getInputPayloadAs(String.class)))
            .build());
        context.start();

        supply("A", "A1");
        supply("A", "A2");
        supply("A", "A3");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processedOf("A").size() == 2);
        Assert.assertEquals(List.of("A2", "A3"), processedOf("A"));
        Assert.assertEquals(List.of("A1"), handled);
    }

    @Test
    public void givenTheFirstExchangeOfAKeyWaitsAtABarrier_thenTheNextOnesOfThatKeyDoNotEnterTheFlowUntilItHasFinished() {
        final List<String> entered = new CopyOnWriteArrayList<>();
        context.registerFlowDefinition(Pipelite.defineFlow("customer-updates")
            .fromSource("queue://customer-updates", (SourceConcurrencyConfigurer c) -> {
                c.durableInbox(false);
                c.concurrency(4).orderedBy(k -> k.headerName("customer-id"));
            })
            .process("enter", (exchange, contribution) -> {
                entered.add(exchange.getInputPayloadAs(String.class));
                trace.add("enter:" + exchange.getInputPayloadAs(String.class));
            })
            .barrier("wait-for-payment", b -> b
                .correlateBy(c -> c.headerName("customer-id"))
                .await("queue://payment")
                .timeout(Duration.ofMinutes(10)))
            .process("update", processor(null, 0))
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("payment")
            .fromSource("queue://payment")
            .process("idle", (exchange, contribution) -> { })
            .build());
        context.start();

        supply("A", "A1");
        supply("A", "A2");
        supply("B", "B1");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> entered.contains("B1"));
        Assert.assertEquals("A2 is held behind A1, which waits at the barrier", List.of("A1", "B1"), sorted(entered));
        Assert.assertTrue(processed.isEmpty());

        final Exchange payment = context.createExchange("payment-of-A");
        payment.putHeader("customer-id", "A");
        context.supplyExchange("queue://payment", payment);

        // the payment of A is remembered, so A2 goes through the barrier at once when its turn comes
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processedOf("A").size() == 2);
        Assert.assertEquals(List.of("A1", "A2"), processedOf("A"));
        Assert.assertTrue("A1 is updated before A2 enters the flow: " + trace,
            trace.indexOf("update:A1") < trace.indexOf("enter:A2"));
    }

    private static List<String> sorted(List<String> values) {
        final List<String> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return copy;
    }

    @Test
    public void givenAnExchangeIsFilteredOut_thenTheKeyIsLetGo() {
        context.registerFlowDefinition(Pipelite.defineFlow("customer-updates")
            .fromSource("queue://customer-updates", (SourceConcurrencyConfigurer c) -> {
                c.durableInbox(false);
                c.concurrency(4).orderedBy(k -> k.headerName("customer-id"));
            })
            .filter("not-a-skip", "Payload != 'A1'")
            .process("update", processor(null, 0))
            .build());
        context.start();

        supply("A", "A1");
        supply("A", "A2");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processedOf("A").size() == 1);
        Assert.assertEquals(List.of("A2"), processedOf("A"));
    }
}
