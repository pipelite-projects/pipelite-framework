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

import io.pipelite.core.context.PipeliteContext;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Issue #159: a producer that holds a resource has it released. The producer that ends a flow is closed
 * when the context stops, after the flow has stopped working; the producer created to supply a single
 * exchange to a destination is closed right after it delivered it. Before, nothing ever called anything
 * on a producer once it was created, so the Kafka producer, which holds a connection and a thread,
 * leaked every time.
 */
public class PipeliteProducerLifecycleIntegrationTest {

    private PipeliteContext context;

    @Before
    public void setup() {
        ProducerLifecycleChannelAdapter.reset();
        context = Pipelite.createContext();
    }

    @After
    public void tearDown() {
        context.stop();
    }

    @Test
    public void givenAFlowEndsInAProducer_thenItIsNotClosedWhileTheContextRunsAndIsClosedOnceWhenItStops() {
        context.registerFlowDefinition(Pipelite.defineFlow("lifecycle-flow")
            .fromSource("queue://lifecycle-in?durableInbox=false")
            .process("pass", (exchange, contribution) -> { })
            .toSink("producer-lifecycle://out")
            .build());
        context.start();
        context.supplyExchange("queue://lifecycle-in", context.createExchange("payload"));
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> events().contains("produce"));

        Assert.assertEquals("one producer for the flow", 1, ProducerLifecycleChannelAdapter.CREATED.get());
        Assert.assertFalse("a producer in use is not closed", events().contains("close"));

        context.stop();

        Assert.assertEquals(java.util.List.of("produce", "close"), events());
    }

    @Test
    public void givenAnExchangeIsSuppliedToADestination_thenTheProducerCreatedForItIsClosedRightAfter() {
        context.start();

        context.supplyExchange("producer-lifecycle://out", context.createExchange("payload"));

        Assert.assertEquals(1, ProducerLifecycleChannelAdapter.CREATED.get());
        Assert.assertEquals(java.util.List.of("produce", "close"), events());
    }

    private static List<String> events() {
        return ProducerLifecycleChannelAdapter.EVENTS;
    }
}
