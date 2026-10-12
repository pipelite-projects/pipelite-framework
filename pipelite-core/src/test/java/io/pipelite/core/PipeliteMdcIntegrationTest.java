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
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.MDC;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Characterization written before the consumer runtime moves (issue #159): while an exchange of an
 * event-driven source runs through the flow, the SLF4J MDC carries {@code pipelite.exchangeId} (the
 * id of the exchange's message) and {@code pipelite.flowName}, and both are cleared afterwards on
 * the dispatching thread. Logs are correlated through these two keys, so they are part of what the
 * restructuring has to keep.
 */
public class PipeliteMdcIntegrationTest {

    private PipeliteContext context;

    @After
    public void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    public void givenAnEventDrivenSource_thenTheMdcCarriesTheExchangeIdAndTheFlowNameWhileTheFlowRuns() {
        final AtomicReference<String> exchangeIdSeen = new AtomicReference<>();
        final AtomicReference<String> flowNameSeen = new AtomicReference<>();

        context = Pipelite.createContext();
        context.registerFlowDefinition(Pipelite.defineFlow("mdc-flow")
            .fromSource("queue://mdc-queue")
            .process("capture", (exchange, contribution) -> {
                exchangeIdSeen.set(MDC.get("pipelite.exchangeId"));
                flowNameSeen.set(MDC.get("pipelite.flowName"));
            })
            .build());
        context.start();

        final ExchangeImpl exchange = context.getExchangeFactory().createExchange("payload");
        context.supplyExchange("queue://mdc-queue", exchange);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> flowNameSeen.get() != null);
        Assert.assertEquals("mdc-flow", flowNameSeen.get());
        Assert.assertEquals(exchange.getInput().getId(), exchangeIdSeen.get());
    }
}
