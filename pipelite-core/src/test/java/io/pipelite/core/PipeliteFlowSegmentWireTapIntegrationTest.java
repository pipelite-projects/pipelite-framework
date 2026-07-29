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
import io.pipelite.core.flow.segment.FlowSegment;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.segment.FlowSegmentDefinition;
import io.pipelite.dsl.segment.FlowSegmentDefinitionImpl;
import io.pipelite.dsl.segment.SegmentWireTapStep;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code FlowSegment} has no public DSL entry point - it is a purely internal mechanism (see
 * {@code 2026-Q3-pipelite-flow-segment.md}, "Perche FlowSegment resta un meccanismo interno").
 * Constructed directly here, exactly as a future internal consumer (a refactored
 * {@code SplitterNode}, or a future {@code .parallel(...)}) would. Mirrors
 * {@link PipeliteWireTapIntegrationTest}, but for a {@code SegmentWireTapStep} living inside a
 * segment's inner chain, wired against a real {@code DefaultPipeliteContext} rather than a mock,
 * to keep real end-to-end {@code link://} dispatch coverage.
 */
public class PipeliteFlowSegmentWireTapIntegrationTest {

    private PipeliteContext context;

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
    }

    @Test
    public void shouldForwardToWireTapChannelFromInsideASegment() {

        final AtomicInteger forwardedCount = new AtomicInteger(0);

        final FlowDefinition destination = Pipelite.defineFlow("segment-wiretap-destination-flow")
            .fromSource("segment-wiretap-destination-start")
            .process("process-message", (ioContext, contribution) -> forwardedCount.incrementAndGet())
            .build();

        context.registerFlowDefinition(destination);
        context.start();

        final FlowSegmentDefinition definition = new FlowSegmentDefinitionImpl();
        definition.add(new SegmentWireTapStep("wire-tap-test", "link://segment-wiretap-destination-start"));

        final FlowSegment segment = new FlowSegment(definition);
        segment.setFlowName("segment-wiretap-origin-flow");
        segment.setSourceEndpointResource("segment-wiretap-origin-start");
        segment.setProcessorName("validation");
        segment.setPipeliteContext(context);

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();
        final Exchange exchange = exchangeFactory.createExchange("Hello Pipelite!");

        segment.process(exchange);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> forwardedCount.get() == 1);
    }
}
