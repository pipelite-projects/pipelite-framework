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
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
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
            .fromSource("origin-start")
            .wireTap("wire-tap-test", "link://destination-01-start")
            .process("mock-target", (ioContext, contribution) -> originalProcessed.set(true))
            .build();

        final FlowDefinition destination01 = Pipelite.defineFlow("destination-01-flow")
            .fromSource("destination-01-start")
            .process("process-message", (ioContext, contribution) -> forwardedCount.incrementAndGet())
            .build();

        context.registerFlowDefinition(origin);
        context.registerFlowDefinition(destination01);
        context.start();

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();

        final Exchange exchange = exchangeFactory.createExchange("Hello Pipelite!");
        context.supplyExchange("origin-start", exchange);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> forwardedCount.get() == 1);
        Assert.assertTrue(originalProcessed.get());

    }

}
