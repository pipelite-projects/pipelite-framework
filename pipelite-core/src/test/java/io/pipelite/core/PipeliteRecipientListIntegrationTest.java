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
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PipeliteRecipientListIntegrationTest {

    private PipeliteContext context;

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
    }

    /** Issue #151: a fixed set of destinations is written in one call, with no lambda and no condition. */
    @Test
    public void shouldForwardToAFixedSetOfRecipientsWithNoCondition(){

        final AtomicInteger forwardedCount = new AtomicInteger(0);

        final FlowDefinition origin = Pipelite.defineFlow("fixed-origin-flow")
            .fromSource("queue://fixed-origin-start")
            .toRecipientList("queue://fixed-a-start", "queue://fixed-b-start", "queue://fixed-c-start")
            .build();

        for (String name : new String[]{"a", "b", "c"}) {
            context.registerFlowDefinition(Pipelite.defineFlow("fixed-" + name + "-flow")
                .fromSource("queue://fixed-" + name + "-start")
                .process("process-message", (ioContext, contribution) -> forwardedCount.incrementAndGet())
                .build());
        }
        context.registerFlowDefinition(origin);
        context.start();

        context.supplyExchange("queue://fixed-origin-start", context.createExchange("Hello Pipelite!"));

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> forwardedCount.get() == 3);
    }

    @Test
    public void shouldGiveEveryRecipientACopyOfTheExchangeWithItsHeadersAndPayload(){

        final java.util.List<String> received = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        context.registerFlowDefinition(Pipelite.defineFlow("copy-origin-flow")
            .fromSource("queue://copy-origin-start")
            .toRecipientList("queue://copy-a-start", "queue://copy-b-start")
            .build());
        for (String name : new String[]{"a", "b"}) {
            context.registerFlowDefinition(Pipelite.defineFlow("copy-" + name + "-flow")
                .fromSource("queue://copy-" + name + "-start")
                .process("record", (ioContext, contribution) ->
                    received.add(name + ":" + ioContext.getInputPayload() + ":" + ioContext.tryGetHeader("X-Tenant").orElse("-")))
                .build());
        }
        context.start();

        final io.pipelite.dsl.Exchange exchange = context.createExchange("Hello Pipelite!");
        exchange.putHeader("X-Tenant", "acme");
        context.supplyExchange("queue://copy-origin-start", exchange);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> received.size() == 2);
        org.junit.Assert.assertTrue(received.contains("a:Hello Pipelite!:acme"));
        org.junit.Assert.assertTrue(received.contains("b:Hello Pipelite!:acme"));
    }

}
