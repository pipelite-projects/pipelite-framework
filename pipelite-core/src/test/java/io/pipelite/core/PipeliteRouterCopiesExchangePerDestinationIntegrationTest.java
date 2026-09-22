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
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Issue #106: {@code toRoute(...)} used to hand the very same {@code ExchangeImpl} instance to
 * every destination of the route it picked, so the flows behind them mutated one shared, mutable
 * object concurrently - the same class of bug {@code RecipientListRouterNode} and
 * {@code WireTapProcessorNode} already avoid by copying.
 */
public class PipeliteRouterCopiesExchangePerDestinationIntegrationTest {

    private PipeliteContext context;

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
    }

    @Test
    public void givenARouteWithTwoDestinations_thenEachFlowReceivesItsOwnInstance() {

        final List<Integer> seenIdentities = new CopyOnWriteArrayList<>();
        final List<String> seenOutputs = new CopyOnWriteArrayList<>();

        final FlowDefinition router = Pipelite.defineFlow("router-flow")
            .fromSource("queue://router-in")
            .toRoute(routes -> routes.dynamic()
                .when("Headers['x'] == 'y'").then("queue://a-in", "queue://b-in")
                .otherwise("queue://a-in")
                .end())
            .build();

        final FlowDefinition flowA = Pipelite.defineFlow("a-flow")
            .fromSource("queue://a-in")
            .process("see", (io, c) -> {
                seenIdentities.add(System.identityHashCode(io));
                // Mutates the exchange it received: if it were shared with b-flow, this would
                // race with b-flow's own mutation below.
                io.setOutputPayload("from-a");
                seenOutputs.add(io.getInputPayloadAs(String.class) + ":from-a");
            })
            .build();

        final FlowDefinition flowB = Pipelite.defineFlow("b-flow")
            .fromSource("queue://b-in")
            .process("see", (io, c) -> {
                seenIdentities.add(System.identityHashCode(io));
                io.setOutputPayload("from-b");
                seenOutputs.add(io.getInputPayloadAs(String.class) + ":from-b");
            })
            .build();

        context.registerFlowDefinition(router);
        context.registerFlowDefinition(flowA);
        context.registerFlowDefinition(flowB);
        context.start();

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();
        final var exchange = exchangeFactory.createExchange("order-1");
        exchange.putHeader("x", "y");
        context.supplyExchange("queue://router-in", exchange);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> seenIdentities.size() == 2);

        assertNotEquals("a-flow and b-flow must not receive the same exchange instance",
            seenIdentities.get(0), seenIdentities.get(1));
        // Each flow's own output is intact: neither saw the other's mutation.
        assertEquals(List.of("order-1:from-a", "order-1:from-b"),
            seenOutputs.stream().sorted().toList());
    }

}
