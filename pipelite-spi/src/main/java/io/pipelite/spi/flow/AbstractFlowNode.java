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
package io.pipelite.spi.flow;

import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;
import io.pipelite.spi.flow.process.FlowExecutionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

public abstract class AbstractFlowNode implements FlowNode {

    private final Collection<ExchangePreProcessor> exchangePreProcessors;
    private final Collection<ExchangePostProcessor> exchangePostProcessors;

    private String flowName;
    private String sourceEndpointResource;
    private String processorName;
    protected String tag;
    protected FlowNode next;
    protected ExceptionHandler exceptionHandler;

    public AbstractFlowNode() {
        exchangePreProcessors = new ArrayList<>();
        exchangePostProcessors = new ArrayList<>();
    }

    @Override
    public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) {
        if(exchangePreProcessor != null){
            exchangePreProcessors.add(exchangePreProcessor);
        }
    }

    public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor){
        if(exchangePostProcessor != null){
            exchangePostProcessors.add(exchangePostProcessor);
        }
    }

    protected void preProcessExchange(Exchange exchange){

        Preconditions.notNull(flowName, "flowName is required and cannot be null");
        Preconditions.notNull(processorName, "processorName is required and cannot be null");

        final FlowExecutionContext ctx = new FlowExecutionContext(flowName, sourceEndpointResource, processorName);
        exchangePreProcessors
            .stream()
            .sorted(Comparator.comparing(ExchangePreProcessor::getOrder))
            .forEach(exchangePreProcessor -> exchangePreProcessor.preProcess(ctx, exchange));
    }

    protected void postProcessExchange(Exchange exchange){

        Preconditions.notNull(flowName, "flowName is required and cannot be null");
        Preconditions.notNull(processorName, "processorName is required and cannot be null");

        final FlowExecutionContext ctx = new FlowExecutionContext(flowName, sourceEndpointResource, processorName);
        exchangePostProcessors
            .stream()
            .sorted(Comparator.comparing(ExchangePostProcessor::getOrder))
            .forEach(exchangePostProcessor -> exchangePostProcessor.postProcess(ctx, exchange));
    }

    @Override
    public String getFlowName() {
        return this.flowName;
    }

    @Override
    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }

    @Override
    public void setSourceEndpointResource(String sourceEndpointResource) {
        this.sourceEndpointResource = sourceEndpointResource;
    }

    @Override
    public String getProcessorName() {
        return this.processorName;
    }

    @Override
    public void setProcessorName(String processorName) {
        this.processorName = processorName;
    }

    @Override
    public void tag(String tag) {
        this.tag = tag;
    }

    public void setNext(FlowNode next) {
        this.next = next;
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }
}
