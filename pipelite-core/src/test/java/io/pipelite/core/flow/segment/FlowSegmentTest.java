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
package io.pipelite.core.flow.segment;

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.dsl.segment.FlowSegmentDefinition;
import io.pipelite.dsl.segment.FlowSegmentDefinitionImpl;
import io.pipelite.dsl.segment.SegmentStep;
import io.pipelite.dsl.segment.SegmentStepDefinition;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FlowSegmentTest {

    private PipeliteContext pipeliteContext;
    private ExchangeFactory exchangeFactory;

    @Before
    public void setup() {
        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
        pipeliteContext = Mockito.mock(PipeliteContext.class);
        Mockito.when(pipeliteContext.getExchangeFactory()).thenReturn(exchangeFactory);
    }

    private FlowSegment wired(FlowSegmentDefinition definition) {
        final FlowSegment node = new FlowSegment(definition);
        node.setFlowName("test-flow");
        node.setSourceEndpointResource("in");
        node.setProcessorName("segment-step");
        node.setPipeliteContext(pipeliteContext);
        return node;
    }

    private static FlowSegmentDefinition definitionOf(SegmentStepDefinition... steps) {
        final FlowSegmentDefinitionImpl definition = new FlowSegmentDefinitionImpl();
        Collections.addAll(definition, steps);
        return definition;
    }

    private static final class CapturingFlowNode implements FlowNode {

        private Exchange captured;

        @Override
        public void process(Exchange exchange) {
            this.captured = exchange;
        }

        @Override
        public void setNext(FlowNode next) {
        }

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public void setFlowName(String flowName) {
        }

        @Override
        public void setSourceEndpointResource(String sourceEndpointResource) {
        }

        @Override
        public void setProcessorName(String processorName) {
        }

        @Override
        public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) {
        }

        @Override
        public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) {
        }

        Exchange getCaptured() {
            return captured;
        }
    }

    @Test
    public void givenZeroStepSegment_whenProcessedWithoutNext_thenItIsPassThrough() {

        final FlowSegment subject = wired(definitionOf());

        final Exchange exchange = exchangeFactory.createExchange("hello");
        subject.process(exchange);

        assertEquals("hello", exchange.getOutput().getPayloadAs(String.class));
    }

    @Test
    public void givenMultiStepSegment_whenProcessed_thenNextReceivesFullyTransformedExchange() {

        final FlowSegment subject = wired(definitionOf(
            new SegmentStep("double", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) * 2)),
            new SegmentStep("increment", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) + 1))
        ));
        final CapturingFlowNode captor = new CapturingFlowNode();
        subject.setNext(captor);

        final Exchange exchange = exchangeFactory.createExchange(5);
        subject.process(exchange);

        // captor is a bare test double that never itself processes the Exchange it receives,
        // so the segment's result lands on its INPUT (the framework's "previous output becomes
        // next input" convention) - nextExchange(...) always hands the following node a fresh,
        // empty output Message of its own to fill in.
        assertEquals(Integer.valueOf(11), captor.getCaptured().getInputPayloadAs(Integer.class));
    }

    // -------------------------------------------------------------------------
    // Piano-impl Correzione #1: process(Exchange) must forward based on the segment's
    // terminal Exchange, not mutate the Exchange it received - otherwise header/property
    // changes made by any step other than the first would be silently lost.
    // -------------------------------------------------------------------------

    @Test
    public void givenHeaderSetByANonFirstStep_whenProcessed_thenHeaderIsVisibleOnExchangeForwardedToNext() {

        final FlowSegment subject = wired(definitionOf(
            new SegmentStep("noop", (io, c) -> io.setOutputPayload(io.getInputPayload())),
            new SegmentStep("tag", (io, c) -> {
                io.putHeader("X-Tagged", "true");
                io.setOutputPayload(io.getInputPayload());
            })
        ));
        final CapturingFlowNode captor = new CapturingFlowNode();
        subject.setNext(captor);

        final Exchange exchange = exchangeFactory.createExchange("payload");
        subject.process(exchange);

        assertEquals("true", captor.getCaptured().tryGetHeaderAs("X-Tagged", String.class).orElse(null));
        // The header must never leak backward onto the original Exchange reference either -
        // nextExchange(...) only ever copies state forward, never back (same class of identity
        // rule already established for Split/Aggregate's child Exchanges).
        assertFalse(exchange.hasHeader("X-Tagged"));
    }

    @Test
    public void givenMultiStepSegment_whenProcessed_thenFlowNameIsPropagatedToInnerNodes() {

        // If flowName weren't propagated to inner nodes, AbstractFlowNode.preProcessExchange
        // (called by the inner DefaultProcessorNode via AbstractProcessorNode.process) would
        // throw a Preconditions failure on the null flowName - so a clean run is proof of
        // propagation.
        final FlowSegment subject = wired(definitionOf(
            new SegmentStep("step-1", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) * 2)),
            new SegmentStep("step-2", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) + 1))
        ));
        final CapturingFlowNode captor = new CapturingFlowNode();
        subject.setNext(captor);

        final Exchange exchange = exchangeFactory.createExchange(1);
        subject.process(exchange);

        assertEquals(Integer.valueOf(3), captor.getCaptured().getInputPayloadAs(Integer.class));
    }

    @Test
    public void givenItemThrowsUnhandledException_whenProcessedWithoutExceptionHandler_thenExceptionPropagates() {

        final FlowSegment subject = wired(definitionOf(
            new SegmentStep("boom", (io, c) -> { throw new RuntimeException("simulated"); })
        ));

        final Exchange exchange = exchangeFactory.createExchange("payload");

        try {
            subject.process(exchange);
            fail("expected RuntimeException to propagate");
        } catch (RuntimeException expected) {
            assertEquals("simulated", expected.getMessage());
        }
    }

    @Test
    public void givenStepStopsExecution_whenProcessed_thenSegmentDoesNotForwardToNextAndDoesNotThrow() {

        final FlowSegment subject = wired(definitionOf(
            new SegmentStep("stop-it", (io, c) -> c.stopExecution())
        ));
        final CapturingFlowNode captor = new CapturingFlowNode();
        subject.setNext(captor);

        final Exchange exchange = exchangeFactory.createExchange("payload");
        subject.process(exchange);

        assertTrue(captor.getCaptured() == null);
    }

    @Test
    public void givenItemThrowsUnhandledException_whenExceptionHandlerConfigured_thenItIsInvokedExactlyOnceForTheWholeSegment() {

        final FlowSegment subject = wired(definitionOf(
            new SegmentStep("step-1", (io, c) -> io.setOutputPayload(io.getInputPayload())),
            new SegmentStep("boom", (io, c) -> { throw new RuntimeException("simulated"); })
        ));

        final List<Throwable> handled = new ArrayList<>();
        subject.setExceptionHandler((exception, exch) -> handled.add(exception));

        final Exchange exchange = exchangeFactory.createExchange("payload");

        // Must not throw: the exceptionHandler absorbs it.
        subject.process(exchange);

        assertEquals(1, handled.size());
    }
}
