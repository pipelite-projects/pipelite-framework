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

import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.exchange.Message;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Focused on the {@link QueuePressureGate} wiring inside {@code process()}/{@code takeNext()} —
 * end-to-end concurrent dispatch behavior is covered by {@code EventDrivenConsumerServiceTest}.
 */
public class EventDrivenConsumerTest {

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

    private static Exchange exchange(int id) {
        final Message message = new SimpleMessage("Id#" + id);
        message.setPayload("Payload#" + id);
        return new Exchange(message);
    }

    @Test
    public void shouldBlockProcessOnceHighWatermarkReachedAndReleaseAtLowWatermark() throws Exception {

        // queueSize=4 -> QueuePressureGate.withDefaultHysteresis(4): high=4, low=1 (4/4).
        final EventDrivenConsumer consumer = new EventDrivenConsumer(
            new DefaultEndpoint(EndpointURL.parse("start-endpoint")), 4);
        consumer.setFlowName("test-flow");
        consumer.setProcessorName("test-processor");
        consumer.setNext(NO_OP_NEXT);

        // Fill the queue to exactly the high watermark from this thread - none of these may block.
        for (int i = 1; i <= 4; i++) {
            consumer.process(exchange(i));
        }

        final AtomicBoolean fifthAccepted = new AtomicBoolean(false);
        final Thread producer = new Thread(() -> {
            consumer.process(exchange(5)); // size is already at the high watermark: must block
            fifthAccepted.set(true);
        });
        producer.start();

        // Give the producer thread every chance to (wrongly) proceed before asserting it hasn't.
        Thread.sleep(300);
        Assert.assertFalse("5th process() must block: queue is already at the high watermark", fifthAccepted.get());

        // Drain down to 2 (still above the low watermark of 1): must NOT release yet.
        consumer.takeNext();
        consumer.takeNext();
        Thread.sleep(300);
        Assert.assertFalse("must stay blocked above the low watermark despite being under the high watermark", fifthAccepted.get());

        // Drain to the low watermark (1): must release, letting the 5th process() proceed.
        consumer.takeNext();
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(fifthAccepted::get);

        producer.join(2000);
        Assert.assertFalse(producer.isAlive());
    }

    @Test
    public void shouldNeverBlockEnqueueingAPoisonPillRegardlessOfPressure() throws Exception {

        final EventDrivenConsumer consumer = new EventDrivenConsumer(
            new DefaultEndpoint(EndpointURL.parse("start-endpoint")), 4);
        consumer.setFlowName("test-flow");
        consumer.setProcessorName("test-processor");
        consumer.setNext(NO_OP_NEXT);

        // Saturate the queue past the high watermark's trigger point.
        for (int i = 1; i <= 4; i++) {
            consumer.process(exchange(i));
        }

        // Must return immediately despite the queue being under pressure - if it blocked, this
        // test would time out instead of failing cleanly, which is itself the point being tested.
        final Exchange poisonPill = new Exchange(new SimpleMessage("poison"));
        poisonPill.setInputPayload(EventDrivenConsumer.POISON_PILL);
        consumer.process(poisonPill);
    }
}
