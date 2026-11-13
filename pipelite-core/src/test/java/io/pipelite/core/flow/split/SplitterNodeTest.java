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

import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.context.internal.IOKeys;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.exchange.internal.DistributedIdentityGeneratorImpl;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.flow.barrier.ExecutionTracker;
import io.pipelite.core.flow.internal.AbstractFlowNode;
import io.pipelite.dsl.split.SplitHeaders;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The elements of a split flow on as exchanges of their own, one after the other, through the nodes that follow
 * (issue #149).
 */
public class SplitterNodeTest {

    /** What comes after the split: records what reaches it, and can be told to fail for an element. */
    private static final class RecordingNode extends AbstractFlowNode {

        final List<ExchangeImpl> received = new ArrayList<>();
        Object failsOn;

        @Override
        public void process(ExchangeImpl exchange) {
            if (failsOn != null && failsOn.equals(exchange.getInputPayload())) {
                throw new IllegalStateException("simulated failure on " + failsOn);
            }
            received.add(exchange);
        }
    }

    private ExchangeFactory exchangeFactory;
    private InternalPipeliteContext pipeliteContext;
    private final RecordingNode after = new RecordingNode();

    @Before
    public void setup() {
        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
        pipeliteContext = Mockito.mock(InternalPipeliteContext.class);
        Mockito.when(pipeliteContext.getExchangeFactory()).thenReturn(exchangeFactory);
    }

    private SplitterNode wired() {
        final SplitterNode node = new SplitterNode();
        node.setFlowName("test-flow");
        node.setSourceEndpointResource("in");
        node.setProcessorName("split-step");
        node.setPipeliteContext(pipeliteContext);
        node.setNext(after);
        return node;
    }

    @Test
    public void givenACollection_thenEachElementGoesOnAsAnExchangeOfItsOwnInOrder() {
        wired().process(exchangeFactory.createExchange(List.of("a", "b", "c")));

        assertEquals(List.of("a", "b", "c"), after.received.stream().map(ExchangeImpl::getInputPayload).toList());
        assertEquals("three different exchanges", 3, Set.copyOf(after.received.stream().map(e -> e.getInput().getId()).toList()).size());
    }

    @Test
    public void givenAnEmptyCollection_thenNothingGoesOn() {
        wired().process(exchangeFactory.createExchange(List.of()));

        assertTrue(after.received.isEmpty());
    }

    @Test
    public void givenAnElement_thenItCarriesTheIdThePositionAndTheSizeOfTheSplit() {
        final ExchangeImpl parent = exchangeFactory.createExchange(List.of("a", "b"));

        wired().process(parent);

        for (int i = 0; i < 2; i++) {
            final ExchangeImpl element = after.received.get(i);
            assertEquals(parent.getInput().getId(), element.tryGetHeaderAs(SplitHeaders.ID, String.class).orElseThrow());
            assertEquals(Integer.valueOf(i), element.tryGetHeaderAs(SplitHeaders.INDEX, Integer.class).orElseThrow());
            assertEquals(Integer.valueOf(2), element.tryGetHeaderAs(SplitHeaders.SIZE, Integer.class).orElseThrow());
        }
    }

    @Test
    public void givenHeadersOnTheExchange_thenEveryElementHasThemAndNoneChangesTheOthersOrTheParent() {
        final ExchangeImpl parent = exchangeFactory.createExchange(List.of(1, 2));
        parent.putHeader("X-Tenant", "acme");

        wired().process(parent);
        after.received.get(0).putHeader("X-Only-First", "yes");

        assertEquals("acme", after.received.get(0).tryGetHeader("X-Tenant").orElseThrow());
        assertEquals("acme", after.received.get(1).tryGetHeader("X-Tenant").orElseThrow());
        assertFalse(after.received.get(1).hasHeader("X-Only-First"));
        assertFalse(parent.hasHeader("X-Only-First"));
        assertFalse(parent.hasHeader(SplitHeaders.ID));
    }

    @Test
    public void givenPropertiesOnTheExchange_thenTheElementsInheritTheOnesThatBelongToTheWorkAndTheRun() {
        final ExchangeImpl parent = exchangeFactory.createExchange(List.of(1, 2));
        parent.setReturnAddress("queue://reply");
        parent.setProperty("app.property", "value");
        final ExecutionTracker tracker = new ExecutionTracker(Set.of());
        parent.setProperty(IOKeys.EXECUTION_TRACKER_PROPERTY_NAME, tracker);
        parent.setProperty(IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME, "entry-1");
        parent.setProperty(IOKeys.DURABLE_INBOX_RESUPPLIED_ENTRY_ID_PROPERTY_NAME, "entry-0");
        parent.setProperty(IOKeys.FLOW_EXECUTION_ATTEMPT_NUMBER_PROPERTY_NAME, 3);
        parent.setProperty(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, "earlier-step");

        wired().process(parent);

        for (ExchangeImpl element : after.received) {
            assertEquals("queue://reply", element.tryGetReturnAddress().orElseThrow());
            assertEquals("value", element.getProperty("app.property", String.class));
            assertSame("the run is the same one", tracker, ExecutionTracker.of(element));
            assertNull("the acknowledgement of the inbox is the parent's",
                element.getProperty(IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME, String.class));
            assertNull(element.getProperty(IOKeys.DURABLE_INBOX_RESUPPLIED_ENTRY_ID_PROPERTY_NAME, String.class));
            assertNull("an element starts with no attempts",
                element.getProperty(IOKeys.FLOW_EXECUTION_ATTEMPT_NUMBER_PROPERTY_NAME, Integer.class));
            assertNull(element.getProperty(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, String.class));
        }
        assertEquals("the parent keeps its inbox entry", "entry-1",
            parent.getProperty(IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME, String.class));
    }

    @Test
    public void givenAnIdempotencyKey_thenEachElementHasTheKeyFollowedByItsPosition() {
        final ExchangeImpl parent = exchangeFactory.createExchange(List.of("a", "b"));
        parent.setProperty(IOKeys.IDEMPOTENCY_KEY_PROPERTY_NAME, "order-7");

        wired().process(parent);

        assertEquals("order-7-0", after.received.get(0).getIdempotencyKey());
        assertEquals("order-7-1", after.received.get(1).getIdempotencyKey());
        assertEquals("order-7", parent.getIdempotencyKey());
    }

    @Test
    public void givenAPayloadThatIsNotACollection_thenTheFailureIsHandledOnceForTheExchange() {
        final SplitterNode subject = wired();
        final List<Throwable> handled = new ArrayList<>();
        subject.setExceptionHandler((exception, exchange) -> handled.add(exception));

        subject.process(exchangeFactory.createExchange("not a collection"));

        assertEquals(1, handled.size());
        assertTrue(handled.get(0).getMessage(), handled.get(0).getMessage().contains("Collection"));
        assertTrue(after.received.isEmpty());
    }

    @Test
    public void givenAPayloadThatIsNotACollectionAndNoHandler_thenTheFailureEscapes() {
        assertThrows(IllegalArgumentException.class, () -> wired().process(exchangeFactory.createExchange("not a collection")));
    }

    @Test
    public void givenAStepAfterTheSplitThatFailsForAnElementWithoutHandler_thenTheFailureEscapesAndTheLaterElementsAreNotSent() {
        after.failsOn = "b";

        assertThrows(IllegalStateException.class, () -> wired().process(exchangeFactory.createExchange(List.of("a", "b", "c"))));

        assertEquals(List.of("a"), after.received.stream().map(ExchangeImpl::getInputPayload).toList());
    }

    @Test
    public void givenNothingFollowsTheSplit_thenItDoesNothing() {
        final SplitterNode subject = new SplitterNode();
        subject.setFlowName("test-flow");
        subject.setProcessorName("split-step");
        subject.setPipeliteContext(pipeliteContext);

        subject.process(exchangeFactory.createExchange(List.of(1, 2)));

        assertTrue(after.received.isEmpty());
    }

    @Test
    public void givenTheElementsShareThePayloadObjects_thenTheyAreNotCopied() {
        final Object element = new Object();

        wired().process(exchangeFactory.createExchange(List.of(element)));

        assertSame("the payload of an element is the object that was in the collection", element, after.received.get(0).getInputPayload());
        assertNotSame(element, after.received.get(0));
    }
}
