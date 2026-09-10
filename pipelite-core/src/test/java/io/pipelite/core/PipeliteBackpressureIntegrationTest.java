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
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * End-to-end verification of #63's {@code QueuePressureGate}, exercised through real flows and
 * {@code PipeliteContext} rather than calling {@code EventDrivenConsumer}/{@code
 * DefaultPollingConsumer} directly (already covered at the unit level by {@code
 * EventDrivenConsumerTest}/{@code DefaultPollingConsumerTest}/{@code QueuePressureGateTest}).
 */
public class PipeliteBackpressureIntegrationTest {

    private PipeliteContext context;

    @After
    public void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    /**
     * "relay-flow" does nothing but forward every Exchange to "slow-sink-flow" via {@code
     * link://} — no processing of its own. "slow-sink-flow" processes deliberately slowly. Before
     * #63, neither flow's intake queue applied any backpressure (both unbounded in practice), so
     * feeding messages into "relay-source" as fast as possible would return near-instantly
     * regardless of how far behind "slow-sink-flow" was — this is exactly the risk #63's own
     * description warns about ("nothing signals slow down back to the actual source"). With the
     * gate wired in, "slow-sink-flow"'s saturated queue blocks relay-flow's single dispatch
     * thread trying to hand it exchanges, which in turn saturates relay-flow's own queue, which
     * in turn must block this test's own {@code supplyExchange(...)} calls — backpressure
     * cascading two hops back to the original producer, exactly as it would for a real HTTP
     * handler thread or an actual {@code link://}-producing flow.
     */
    @Test
    public void shouldPropagateBackpressureThroughALinkHopBackToTheOriginalProducer() {

        final int messages = 100;
        final long slowStepMillis = 30;
        final AtomicInteger processedCount = new AtomicInteger(0);

        final FlowDefinition relay = Pipelite.defineFlow("relay-flow")
            .fromSource("relay-source")
            .toSink("link://slow-sink")
            .build();

        final FlowDefinition slowSink = Pipelite.defineFlow("slow-sink-flow")
            .fromSource("slow-sink")
            .process("slow-step", (ioContext, contribution) -> {
                try {
                    Thread.sleep(slowStepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                processedCount.incrementAndGet();
            })
            .build();

        context = new DefaultPipeliteContext();
        context.registerFlowDefinition(relay);
        context.registerFlowDefinition(slowSink);
        context.start();

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();

        final long feedStart = System.nanoTime();
        for (int i = 0; i < messages; i++) {
            context.supplyExchange("relay-source", exchangeFactory.createExchange("message-" + i));
        }
        final long feedElapsedMillis = (System.nanoTime() - feedStart) / 1_000_000;

        // Lower bound picked well below the fully-serialized worst case (messages * slowStepMillis
        // = 3000ms) but far above what near-instant, backpressure-free enqueueing could ever take
        // (single-digit milliseconds for 100 in-memory queue.put() calls) - margin on both sides
        // to avoid flakiness while still only passing if the feeding loop itself genuinely blocked.
        Assert.assertTrue(
            "expected the feeding loop itself to block under backpressure (took " + feedElapsedMillis
                + "ms for " + messages + " messages) - a value this low means supplyExchange() "
                + "never blocked, i.e. no backpressure propagated back to the producer",
            feedElapsedMillis > 500);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processedCount.get() == messages);
        Assert.assertEquals(messages, processedCount.get());
    }
}
