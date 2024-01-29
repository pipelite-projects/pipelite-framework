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
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduledPollingConsumerService extends AbstractService implements PollingConsumer {

    protected static final String INITIAL_DELAY_PROPERTY_NAME = "initialDelay";
    protected static final String PERIOD_PROPERTY_NAME = "period";
    protected static final String TIME_UNIT_PROPERTY_NAME = "timeUnit";

    protected final PollingConsumer pollingConsumer;
    protected final ScheduledExecutorService consumerPool;
    private final Collection<ScheduledFuture<?>> scheduledWorkers;

    public ScheduledPollingConsumerService(PollingConsumer pollingConsumer, ScheduledExecutorService consumerPool) {
        Objects.requireNonNull(pollingConsumer, "pollingConsumer is required and cannot be null");
        Objects.requireNonNull(consumerPool, "consumerPool is required and cannot be null");
        this.pollingConsumer = pollingConsumer;
        this.consumerPool = consumerPool;
        this.scheduledWorkers = new ArrayList<>();
    }

    @Override
    public void doStart() {

        final Endpoint endpoint = pollingConsumer.getEndpoint();
        final EndpointProperties endpointProperties = endpoint.getProperties();

        final Long period = endpointProperties.getAsLongOrDefault(PERIOD_PROPERTY_NAME, 1000L);
        final Long initialDelay = endpointProperties.getAsLongOrDefault(INITIAL_DELAY_PROPERTY_NAME, 0L);

        final String timeUnitAsText = endpointProperties.getOrDefault(TIME_UNIT_PROPERTY_NAME, TimeUnit.MILLISECONDS.name());
        final TimeUnit timeUnit = TimeUnit.valueOf(timeUnitAsText);

        ScheduledFuture<?> consumer = consumerPool.scheduleAtFixedRate(() -> {
            final Exchange exchange = pollingConsumer.receive();
            if(exchange != null){
                pollingConsumer.process(exchange);
            }
        }, initialDelay, period, timeUnit);
        addScheduledWorker(consumer);
    }

    protected final void addScheduledWorker(ScheduledFuture<?> worker){
        scheduledWorkers.add(worker);
    }

    @Override
    public void doStop() {
        scheduledWorkers.forEach(scheduledFuture -> scheduledFuture.cancel(true));
    }

    @Override
    public Endpoint getEndpoint() {
        return pollingConsumer.getEndpoint();
    }

    @Override
    public void consume(Exchange exchange) {
        pollingConsumer.consume(exchange);
    }

    @Override
    public void process(Exchange exchange) {
        pollingConsumer.process(exchange);
    }

    @Override
    public void setFlowName(String flowName) {
        pollingConsumer.setFlowName(flowName);
    }

    @Override
    public void setSourceEndpointResource(String sourceEndpointResource) {
        pollingConsumer.setSourceEndpointResource(sourceEndpointResource);
    }

    @Override
    public void setProcessorName(String processorName) {
        pollingConsumer.setProcessorName(processorName);
    }

    @Override
    public void setSequenceNumber(int sequenceNumber) {
        pollingConsumer.setSequenceNumber(sequenceNumber);
    }

    @Override
    public int getSequenceNumber() {
        return pollingConsumer.getSequenceNumber();
    }

    @Override
    public void setNext(FlowNode next) {
        pollingConsumer.setNext(next);
    }

    @Override
    public boolean hasNext() {
        return pollingConsumer.hasNext();
    }

    @Override
    public void tag(String tag) {
        pollingConsumer.tag(tag);
    }

    @Override
    public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) {
        pollingConsumer.addExchangePreProcessor(exchangePreProcessor);
    }

    @Override
    public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) {
        pollingConsumer.addExchangePostProcessor(exchangePostProcessor);
    }

    @Override
    public Exchange receive() {
        synchronized (this){
            return pollingConsumer.receive();
        }
    }

    @Override
    public Exchange receive(long timeout) {
        return pollingConsumer.receive(timeout);
    }

    @Override
    public Exchange receiveNoWait() {
        return pollingConsumer.receiveNoWait();
    }

    @Override
    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        pollingConsumer.setExceptionHandler(exceptionHandler);
    }
}
