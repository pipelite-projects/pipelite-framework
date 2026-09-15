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
package io.pipelite.spi.endpoint;

import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.exchange.IdentityGenerator;
import io.pipelite.spi.flow.exchange.Message;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;
import io.pipelite.spi.inbox.DurableInbox;
import io.pipelite.spi.inbox.SegmentedLogDurableInbox;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.Map;

/**
 * Regression coverage for a real bug found while implementing issue #70: a plain, uncopied
 * forward to another flow's consumer — e.g. {@code LinkProducer}'s {@code .toSink("link://...")},
 * unlike {@code WireTapProcessorNode}, which deliberately taps a <em>copy</em> — hands the exact
 * same {@code Exchange} instance to the destination consumer. The destination's own write-through
 * hook then overwrites {@code IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME} on that shared instance
 * with its own entry id, before control ever returns to the origin's {@code dispatchToNext}. If
 * that method re-read the property off {@code exchange} at that point (the original
 * implementation), it would acknowledge the destination's entry against the origin's own inbox — a
 * no-op there — leaving the origin's real entry permanently pending. Reproduced concretely via
 * {@code PipeliteSplitAggregateExceptionHandlingTest}-adjacent flows sharing generic resource names
 * across test classes before this was found. Fixed by capturing the entry id into a local variable
 * before dispatch instead of reading it back off the (by-then possibly mutated) exchange.
 */
public class EventDrivenConsumerDurableInboxTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final FlowNode NO_OP_NEXT = new FlowNode() {
        @Override public void process(Exchange exchange) { }
        @Override public void setFlowName(String flowName) { }
        @Override public void setSourceEndpointResource(String sourceEndpointResource) { }
        @Override public void setProcessorName(String processorName) { }
        @Override public void setNext(FlowNode next) { }
        @Override public boolean hasNext() { return false; }
        @Override public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) { }
        @Override public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) { }
    };

    private static Exchange exchange() {
        final Message message = new SimpleMessage("id");
        message.setPayload("payload");
        return new Exchange(message);
    }

    @Test
    public void shouldAcknowledgeOriginatingEntryEvenWhenExchangeIsForwardedUncopiedToAnotherConsumer() {

        final IdentityGenerator identityGenerator = new DistributedIdentityGeneratorImpl();
        // Same shared directory for both, distinguished only by name prefix - exactly how
        // SegmentedLogDurableInboxProvider wires distinct resources in production.
        final Path sharedDirectory = temporaryFolder.getRoot().toPath();
        final DurableInbox originInbox = new SegmentedLogDurableInbox(sharedDirectory, "origin", identityGenerator);
        final DurableInbox destinationInbox = new SegmentedLogDurableInbox(sharedDirectory, "destination", identityGenerator);

        final EventDrivenConsumer destination = new EventDrivenConsumer(new DefaultEndpoint(EndpointURL.parse("destination-endpoint")));
        destination.setFlowName("destination-flow");
        destination.setProcessorName("destination-endpoint");
        destination.setNext(NO_OP_NEXT);
        destination.setDurableInbox(destinationInbox);

        final EventDrivenConsumer origin = new EventDrivenConsumer(new DefaultEndpoint(EndpointURL.parse("origin-endpoint")));
        origin.setFlowName("origin-flow");
        origin.setProcessorName("origin-endpoint");
        // Simulates LinkProducer.process(): hands the SAME Exchange instance straight to another
        // consumer, no exchangeFactory.copyExchange(...) involved.
        origin.setNext(new FlowNode() {
            @Override public void process(Exchange exchange) { destination.consume(exchange); }
            @Override public void setFlowName(String flowName) { }
            @Override public void setSourceEndpointResource(String sourceEndpointResource) { }
            @Override public void setProcessorName(String processorName) { }
            @Override public void setNext(FlowNode next) { }
            @Override public boolean hasNext() { return false; }
            @Override public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) { }
            @Override public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) { }
        });
        origin.setDurableInbox(originInbox);

        final Exchange exchange = exchange();
        origin.process(exchange); // enqueue: write-through hook sets origin's own entry id property
        origin.receive();         // dequeue + dispatchToNext: forwards to destination (which
                                  // overwrites the shared property with ITS OWN entry id), then acks

        Assert.assertTrue("origin's own entry must be acknowledged despite the shared Exchange " +
                "instance being mutated by the destination consumer's own write-through hook",
            originInbox.pendingEntries().isEmpty());
        Assert.assertEquals("destination's entry must still be pending - only enqueued, never " +
                "dispatched further in this test",
            1, destinationInbox.pendingEntries().size());
    }

    @Test
    public void shouldTagEnqueuedEntryWithExchangeIdAndFlowNameForDebugging() {

        final DurableInbox inbox = new SegmentedLogDurableInbox(
            temporaryFolder.getRoot().toPath(), "origin", new DistributedIdentityGeneratorImpl());

        final EventDrivenConsumer consumer = new EventDrivenConsumer(new DefaultEndpoint(EndpointURL.parse("origin-endpoint")));
        consumer.setFlowName("origin-flow");
        consumer.setProcessorName("origin-endpoint");
        consumer.setNext(NO_OP_NEXT);
        consumer.setDurableInbox(inbox);

        consumer.process(exchange());

        final Map<String, String> metadata = inbox.pendingEntries().get(0).getMetadata();
        Assert.assertEquals("id", metadata.get("exchangeId"));
        Assert.assertEquals("origin-flow", metadata.get("flowName"));
    }

}
