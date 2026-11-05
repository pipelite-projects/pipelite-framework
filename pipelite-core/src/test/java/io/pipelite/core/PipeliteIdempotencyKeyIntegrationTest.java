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

import io.pipelite.common.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.common.support.serialization.ObjectToByteArrayConverter;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.flow.barrier.BarrierInMemoryRepository;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.definition.SourceConcurrencyConfigurer;
import io.pipelite.dsl.definition.builder.Backoff;
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

/**
 * Issue #40: the key that a {@code Processor} gives an external system is the same each time the work is repeated by the
 * framework: in every step, in a retry, after a barrier, and in the flow that receives the exchange.
 */
public class PipeliteIdempotencyKeyIntegrationTest {

    private DefaultPipeliteContext context;
    private final List<String> seen = new CopyOnWriteArrayList<>();

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
        context.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());
        context.setBarrierRepository(new BarrierInMemoryRepository());
        context.setDeadLetterQueueRepository(entry -> { });
    }

    @After
    public void tearDown() {
        context.stop();
    }

    private static void durableInboxOff(SourceConcurrencyConfigurer c) {
        c.durableInbox(false);
    }

    private void supply(String queue, String payload) {
        context.supplyExchange("queue://" + queue, context.createExchange(payload));
    }

    private List<String> keysOf(String step) {
        return seen.stream().filter(entry -> entry.startsWith(step + "=")).map(entry -> entry.substring(step.length() + 1)).toList();
    }

    @Test
    public void givenAFlowWithSeveralSteps_thenEveryStepSeesTheSameKeyAndEachExchangeHasItsOwn() {
        context.registerFlowDefinition(Pipelite.defineFlow("checkout")
            .fromSource("queue://checkout", PipeliteIdempotencyKeyIntegrationTest::durableInboxOff)
            .process("reserve", (exchange, contribution) -> seen.add("reserve=" + exchange.getIdempotencyKey()))
            .transformPayload("upper", payload -> String.valueOf(payload).toUpperCase())
            .process("charge", (exchange, contribution) -> seen.add("charge=" + exchange.getIdempotencyKey()))
            .build());
        context.start();

        supply("checkout", "order-1");
        supply("checkout", "order-2");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> keysOf("charge").size() == 2);
        Assert.assertEquals("every step of a flow sees the same key", keysOf("reserve"), keysOf("charge"));
        Assert.assertNotEquals("each piece of work has its own", keysOf("charge").get(0), keysOf("charge").get(1));
        Assert.assertTrue(keysOf("charge").stream().noneMatch(key -> key == null || key.isBlank()));
    }

    @Test
    public void givenAStepIsRetried_thenItSeesTheKeyOfTheFirstAttempt() {
        final AtomicInteger attempts = new AtomicInteger();
        context.registerFlowDefinition(Pipelite.defineFlow("checkout")
            .fromSource("queue://checkout", PipeliteIdempotencyKeyIntegrationTest::durableInboxOff)
            .process("reserve", (exchange, contribution) -> seen.add("reserve=" + exchange.getIdempotencyKey()))
            .process("charge", (exchange, contribution) -> {
                seen.add("charge=" + exchange.getIdempotencyKey());
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("the gateway did not answer");
                }
            })
            .withRetry(retry -> retry
                .maxAttempts(3)
                .backoff(Backoff.linear(Duration.ofSeconds(1)))
                .onErrorChannel(err -> err.toDLQ()))
            .build());
        context.start();

        supply("checkout", "order-1");

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> keysOf("charge").size() == 2);
        Assert.assertEquals("the retry repeats the call with the same key", keysOf("charge").get(0), keysOf("charge").get(1));
        Assert.assertEquals("and it is the key of the step before", keysOf("reserve").get(0), keysOf("charge").get(0));
    }

    @Test
    public void givenTheExchangeWaitsAtABarrier_thenItHasTheSameKeyWhenItGoesOn() {
        context.registerFlowDefinition(Pipelite.defineFlow("checkout")
            .fromSource("queue://checkout", PipeliteIdempotencyKeyIntegrationTest::durableInboxOff)
            .process("before", (exchange, contribution) -> seen.add("before=" + exchange.getIdempotencyKey()))
            .barrier("payment", b -> b
                .correlateBy(c -> c.headerName("order-id"))
                .await("queue://payment")
                .timeout(Duration.ofMinutes(10)))
            .process("after", (exchange, contribution) -> seen.add("after=" + exchange.getIdempotencyKey()))
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("payment")
            .fromSource("queue://payment")
            .process("idle", (exchange, contribution) -> { })
            .build());
        context.start();

        final Exchange order = context.createExchange("order-1");
        order.putHeader("order-id", "o-1");
        context.supplyExchange("queue://checkout", order);
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> keysOf("before").size() == 1);
        final Exchange payment = context.createExchange("paid");
        payment.putHeader("order-id", "o-1");
        context.supplyExchange("queue://payment", payment);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> keysOf("after").size() == 1);
        Assert.assertEquals(keysOf("before"), keysOf("after"));
    }

    @Test
    public void givenAFlowHandsTheExchangeOver_thenTheOtherFlowKeepsTheKeyEvenWhenTheHandOverIsRepeated() throws Exception {
        context.registerFlowDefinition(Pipelite.defineFlow("checkout")
            .fromSource("queue://checkout", PipeliteIdempotencyKeyIntegrationTest::durableInboxOff)
            .process("reserve", (exchange, contribution) -> seen.add("reserve=" + exchange.getIdempotencyKey()))
            .toSink("queue://billing")
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("billing")
            .fromSource("queue://billing", PipeliteIdempotencyKeyIntegrationTest::durableInboxOff)
            .process("charge", (exchange, contribution) -> seen.add("charge=" + exchange.getIdempotencyKey()))
            .build());
        context.start();

        final Exchange order = context.createExchange("order-1");
        context.supplyExchange("queue://checkout", order);
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> keysOf("charge").size() == 1);

        // the first flow replays after a restart and hands the same order over again: what the inbox of the first flow
        // gives back is the exchange as it was written, with its key
        final byte[] written = new ObjectToByteArrayConverter().convert(order);
        final ExchangeImpl replayed = new ByteArrayToObjectConverter().convert(written, ExchangeImpl.class);
        context.supplyExchange("queue://checkout", replayed);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> keysOf("charge").size() == 2);
        Assert.assertEquals("the second flow sees the same key both times", keysOf("charge").get(0), keysOf("charge").get(1));
        Assert.assertEquals(keysOf("reserve").get(0), keysOf("charge").get(0));
    }

    @Test
    public void givenASplit_thenEachElementHasAKeyOfItsOwnDerivedFromTheOneOfTheExchange() {
        context.registerFlowDefinition(Pipelite.defineFlow("checkout")
            .fromSource("queue://checkout", PipeliteIdempotencyKeyIntegrationTest::durableInboxOff)
            .process("before", (exchange, contribution) -> seen.add("before=" + exchange.getIdempotencyKey()))
            .split("charge-each", segment -> segment
                .process("charge", (exchange, contribution) -> seen.add("charge=" + exchange.getIdempotencyKey()))
                .end())
            .process("after", (exchange, contribution) -> seen.add("after=" + exchange.getIdempotencyKey()))
            .build());
        context.start();

        context.supplyExchange("queue://checkout", context.createExchange(List.of("a", "b", "c")));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> keysOf("after").size() == 1);
        final String key = keysOf("before").get(0);
        Assert.assertEquals(List.of(key + "-0", key + "-1", key + "-2"), keysOf("charge"));
        Assert.assertEquals("after the split it is the work of the exchange again", key, keysOf("after").get(0));
    }
}
