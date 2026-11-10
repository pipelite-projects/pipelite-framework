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

import io.pipelite.core.context.internal.InternalPipeliteContext;

import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PipeliteWireTapIntegrationTest {

    private PipeliteContext context;

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
    }

    @Test
    public void shouldForwardToWireTapChannel(){

        final AtomicInteger forwardedCount = new AtomicInteger(0);
        final AtomicBoolean originalProcessed = new AtomicBoolean(false);

        final FlowDefinition origin = Pipelite.defineFlow("origin-flow")
            .fromSource("queue://origin-start")
            .wireTap("wire-tap-test", "queue://destination-01-start")
            .process("mock-target", (ioContext, contribution) -> originalProcessed.set(true))
            .build();

        final FlowDefinition destination01 = Pipelite.defineFlow("destination-01-flow")
            .fromSource("queue://destination-01-start")
            .process("process-message", (ioContext, contribution) -> forwardedCount.incrementAndGet())
            .build();

        context.registerFlowDefinition(origin);
        context.registerFlowDefinition(destination01);
        context.start();

        final ExchangeFactory exchangeFactory = ((InternalPipeliteContext) context).getExchangeFactory();

        final ExchangeImpl exchange = exchangeFactory.createExchange("Hello Pipelite!");
        context.supplyExchange("queue://origin-start", exchange);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> forwardedCount.get() == 1);
        Assert.assertTrue(originalProcessed.get());

    }


    /**
     * Issue #34: the tap inspects the exchange, it is not a step of the flow. A tap that cannot be delivered is logged,
     * the exchange goes on to the next node, and the flow's exception handler is not called.
     */
    @Test
    public void givenATapThatCannotBeDelivered_thenTheFlowGoesOnAndTheExceptionHandlerIsNotCalled(){

        final AtomicBoolean handled = new AtomicBoolean(false);
        final AtomicBoolean nextProcessed = new AtomicBoolean(false);

        context.registerFlowDefinition(Pipelite.defineFlow("tapping-flow")
            .fromSource("queue://tapping-in")
            .wireTap("wire-tap-test", "nobody://tap")
            .process("next-step", (ioContext, contribution) -> nextProcessed.set(true))
            .withExceptionHandler((exception, exchange) -> handled.set(true))
            .build());
        context.start();

        context.supplyExchange("queue://tapping-in", ((InternalPipeliteContext) context).getExchangeFactory().createExchange("Hello Pipelite!"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(nextProcessed::get);
        Assert.assertFalse(handled.get());
    }

    @Test
    public void givenATapThatCannotBeDeliveredAndARetry_thenNothingIsRetried(){

        final AtomicInteger stepRuns = new AtomicInteger(0);
        final AtomicInteger nextRuns = new AtomicInteger(0);
        final AtomicBoolean exhausted = new AtomicBoolean(false);

        context.registerFlowDefinition(Pipelite.defineFlow("retrying-tapping-flow")
            .fromSource("queue://retrying-tapping-in")
            .process("count", (ioContext, contribution) -> stepRuns.incrementAndGet())
            .wireTap("wire-tap-test", "nobody://tap")
            .process("next-step", (ioContext, contribution) -> nextRuns.incrementAndGet())
            .withRetry(retry -> retry
                .maxAttempts(2)
                .onExceptionHandler((exception, exchange) -> exhausted.set(true)))
            .build());
        context.start();

        context.supplyExchange("queue://retrying-tapping-in", ((InternalPipeliteContext) context).getExchangeFactory().createExchange("Hello Pipelite!"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> nextRuns.get() == 1);
        Assert.assertEquals(1, stepRuns.get());
        Assert.assertFalse(exhausted.get());
    }

}
