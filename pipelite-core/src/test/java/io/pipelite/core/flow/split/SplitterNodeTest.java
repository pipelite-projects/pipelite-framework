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
package io.pipelite.core.flow.split;

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.dsl.split.SplitSegment;
import io.pipelite.dsl.split.SplitSegmentImpl;
import io.pipelite.dsl.split.SplitStep;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SplitterNodeTest {

    private PipeliteContext pipeliteContext;
    private ExchangeFactory exchangeFactory;
    private AggregateRepository aggregateRepository;

    @Before
    public void setup() {

        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
        aggregateRepository = new AggregateInMemoryRepository();

        // SplitterNode.setPipeliteContext(...) reads getExchangeFactory()/getAggregateRepository()
        // eagerly, unlike RouterNode which just stores the context - both must be stubbed
        // BEFORE setPipeliteContext(...) is called below, or the node's fields stay null.
        pipeliteContext = Mockito.mock(PipeliteContext.class);
        Mockito.when(pipeliteContext.getExchangeFactory()).thenReturn(exchangeFactory);
        Mockito.when(pipeliteContext.getAggregateRepository()).thenReturn(aggregateRepository);
    }

    private SplitterNode wired(SplitSegment segment) {
        final SplitterNode node = new SplitterNode(segment);
        node.setFlowName("test-flow");
        node.setSourceEndpointResource("in");
        node.setProcessorName("split-step");
        node.setPipeliteContext(pipeliteContext);
        return node;
    }

    private static SplitSegment segmentOf(SplitStep... steps) {
        final SplitSegmentImpl segment = new SplitSegmentImpl();
        Collections.addAll(segment, steps);
        return segment;
    }

    @Test
    public void givenHeaderMutatedByAStep_whenProcessingChildren_thenParentExchangeIsNeverMutated() {

        final SplitterNode subject = wired(segmentOf(
            new SplitStep("tag-even", (io, c) -> {
                final int value = io.getInputPayloadAs(Integer.class);
                if (value % 2 == 0) {
                    io.putHeader("X-Even", "true");
                }
                io.setOutputPayload(value);
            })
        ));

        final Exchange exchange = exchangeFactory.createExchange(List.of(1, 2, 3, 4));
        subject.process(exchange);

        // A header set on a child (via the defensive Headers copy) must never leak back
        // onto the parent Exchange - same class of bug already fixed in #30 for copyExchange.
        assertFalse(exchange.hasHeader("X-Even"));
        assertEquals(List.of(1, 2, 3, 4), exchange.getOutput().getPayloadAs(List.class));
    }

    @Test
    public void givenMultiStepSegment_whenProcessed_thenFlowNameIsPropagatedToInnerNodes() {

        // If flowName weren't propagated to inner nodes, AbstractFlowNode.preProcessExchange
        // (called by the inner DefaultProcessorNode via AbstractProcessorNode.process) would
        // throw a Preconditions failure on the null flowName - so a clean run is proof of
        // propagation.
        final SplitterNode subject = wired(segmentOf(
            new SplitStep("step-1", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) * 2)),
            new SplitStep("step-2", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) + 1))
        ));

        final Exchange exchange = exchangeFactory.createExchange(List.of(1, 2, 3));
        subject.process(exchange);

        assertEquals(List.of(3, 5, 7), exchange.getOutput().getPayloadAs(List.class));
    }

    @Test
    public void givenZeroStepSegment_whenProcessed_thenItIsPassThrough() {

        final SplitterNode subject = wired(segmentOf());

        final Exchange exchange = exchangeFactory.createExchange(List.of("a", "b", "c"));
        subject.process(exchange);

        assertEquals(List.of("a", "b", "c"), exchange.getOutput().getPayloadAs(List.class));
    }

    @Test
    public void givenEmptyCollection_whenProcessed_thenAggregatedPayloadIsEmpty() {

        final SplitterNode subject = wired(segmentOf(
            new SplitStep("noop", (io, c) -> io.setOutputPayload(io.getInputPayload()))
        ));

        final Exchange exchange = exchangeFactory.createExchange(List.of());
        subject.process(exchange);

        assertEquals(List.of(), exchange.getOutput().getPayloadAs(List.class));
    }

    @Test
    public void givenSuccessfulSplit_whenProcessed_thenRepositoryHasNoOrphanEntry() {

        final SplitterNode subject = wired(segmentOf());
        final Exchange exchange = exchangeFactory.createExchange(List.of(1, 2, 3));
        final String id = exchange.getInput().getId();

        subject.process(exchange);

        assertTrue(aggregateRepository.tryLoad(id).isEmpty());
    }

    @Test
    public void givenItemThrowsUnhandledException_whenProcessedWithoutExceptionHandler_thenExceptionPropagatesAndRepositoryHasNoOrphanEntry() {

        final SplitterNode subject = wired(segmentOf(
            new SplitStep("boom", (io, c) -> { throw new RuntimeException("simulated"); })
        ));

        final Exchange exchange = exchangeFactory.createExchange(List.of(1));
        final String id = exchange.getInput().getId();

        try {
            subject.process(exchange);
            fail("expected RuntimeException to propagate");
        } catch (RuntimeException expected) {
            assertEquals("simulated", expected.getMessage());
        }

        assertTrue(aggregateRepository.tryLoad(id).isEmpty());
    }

    @Test
    public void givenItemThrowsUnhandledException_whenExceptionHandlerConfigured_thenItIsInvokedExactlyOnceForTheWholeSplit() {

        final SplitterNode subject = wired(segmentOf(
            new SplitStep("boom", (io, c) -> { throw new RuntimeException("simulated"); })
        ));

        final java.util.List<Throwable> handled = new java.util.ArrayList<>();
        subject.setExceptionHandler((exception, exch) -> handled.add(exception));

        final Exchange exchange = exchangeFactory.createExchange(List.of(1, 2, 3));
        final String id = exchange.getInput().getId();

        // Must not throw: the exceptionHandler absorbs it.
        subject.process(exchange);

        assertEquals(1, handled.size());
        assertTrue(aggregateRepository.tryLoad(id).isEmpty());
    }
}
