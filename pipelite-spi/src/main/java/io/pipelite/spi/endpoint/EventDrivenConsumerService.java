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

import io.pipelite.spi.context.AbstractService;
import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.concurrent.DefaultThreadFactory;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;

public class EventDrivenConsumerService extends AbstractService implements Consumer, ExchangeFactoryAware {

    private static final String WORKER_PREFIX = "EDC";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final class EventDrivenConsumerReceiveTask implements Runnable {

        @Override
        public void run() {
            if(!isRunAllowed()){
                throw new IllegalStateException("Service run not allowed, status is " + EventDrivenConsumerService.this.getStatus());
            }
            while (isRunAllowed()) {
                if(eventDrivenConsumer.receive() < 1){
                    break;
                }
            }
        }
    }

    private final class EventDrivenConsumerPoisonPillTask implements Runnable {

        @Override
        public void run() {
            final Exchange poisonPill = exchangeFactory.createExchange(EventDrivenConsumer.POISON_PILL);
            eventDrivenConsumer.consume(poisonPill);
        }
    }

    private final EventDrivenConsumer eventDrivenConsumer;
    protected final ThreadFactory threadFactory;
    protected ExchangeFactory exchangeFactory;

    private Thread receiveTask;
    private Thread poisonPillTask;

    public EventDrivenConsumerService(EventDrivenConsumer eventDrivenConsumer) {
        this.eventDrivenConsumer = eventDrivenConsumer;
        threadFactory = new DefaultThreadFactory(WORKER_PREFIX);
    }

    @Override
    public void doStart() {

        if (receiveTask == null) {
            receiveTask = threadFactory.newThread(new EventDrivenConsumerReceiveTask());
        }
        receiveTask.start();

        if(logger.isTraceEnabled()){
            logger.trace("EventDrivenConsumerReceiveTask successfully started");
        }
    }

    @Override
    public void doStop() {

        if (poisonPillTask == null) {
            poisonPillTask = threadFactory.newThread(new EventDrivenConsumerPoisonPillTask());
        }
        poisonPillTask.start();

        if(logger.isTraceEnabled()){
            logger.trace("EventDrivenWorker successfully started");
        }
        //worker.interrupt();
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

    @Override
    public Endpoint getEndpoint() {
        return eventDrivenConsumer.getEndpoint();
    }

    @Override
    public void consume(Exchange exchange) {
        eventDrivenConsumer.consume(exchange);
    }

    @Override
    public void process(Exchange exchange) {
        eventDrivenConsumer.process(exchange);
    }

    @Override
    public void setFlowName(String flowName) {
        eventDrivenConsumer.setFlowName(flowName);
    }

    @Override
    public void setProcessorName(String processorName) {
        eventDrivenConsumer.setProcessorName(processorName);
    }

    @Override
    public void setSequenceNumber(int sequenceNumber) {
        eventDrivenConsumer.setSequenceNumber(sequenceNumber);
    }

    @Override
    public int getSequenceNumber() {
        return eventDrivenConsumer.getSequenceNumber();
    }

    @Override
    public void setSourceEndpointResource(String sourceEndpointResource) {
        eventDrivenConsumer.setSourceEndpointResource(sourceEndpointResource);
    }

    @Override
    public void setNext(FlowNode next) {
        eventDrivenConsumer.setNext(next);
    }

    @Override
    public boolean hasNext() {
        return eventDrivenConsumer.hasNext();
    }

    @Override
    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        eventDrivenConsumer.setExceptionHandler(exceptionHandler);
    }

    @Override
    public void tag(String tag) {
        eventDrivenConsumer.tag(tag);
    }

    @Override
    public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) {
        eventDrivenConsumer.addExchangePreProcessor(exchangePreProcessor);
    }

    @Override
    public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) {
        eventDrivenConsumer.addExchangePostProcessor(exchangePostProcessor);
    }
}
