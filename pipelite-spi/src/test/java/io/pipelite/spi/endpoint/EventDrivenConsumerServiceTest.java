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
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EventDrivenConsumerServiceTest {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private EventDrivenConsumerService subject;

    @Before
    public void setup(){
        final Endpoint endpoint = new DefaultEndpoint(EndpointURL.parse("start-endpoint"));
        final EventDrivenConsumer consumer = new EventDrivenConsumer(endpoint);
        subject = new EventDrivenConsumerService(consumer);
        subject.setFlowName("test-flow");
        subject.setProcessorName("test-processor");
    }

    @Test
    public void shouldProcessInNewThread(){

        final long parentThreadId = Thread.currentThread().getId();
        final AtomicLong consumerThreadId = new AtomicLong();
        final AtomicInteger receivedCount = new AtomicInteger(0);

        subject.setNext(new FlowNode() {

            @Override
            public void process(Exchange exchange) {
                consumerThreadId.set(Thread.currentThread().getId());
                receivedCount.incrementAndGet();
                logger.info("Received {}", exchange.getInputPayload());
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
            public void setNext(FlowNode next) {
            }

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) {
            }

            @Override
            public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) {
            }
        });

        subject.start();

        int numOfMessages = 10;
        for(int i=1;i<=numOfMessages;i++){
            Message inputMessage = new SimpleMessage(String.format("Id#%s", i));
            inputMessage.setPayload(String.format("Payload#%s", i));
            final Exchange exchange = new Exchange(inputMessage);
            subject.consume(exchange);
        }

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> receivedCount.get() == numOfMessages);
        Assert.assertNotEquals(parentThreadId, consumerThreadId.get());

    }
}
