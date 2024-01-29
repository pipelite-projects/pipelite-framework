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
package io.pipelite.core.flow;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.context.EndpointFactory;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.flow.process.AbstractProcessorNode;
import io.pipelite.core.flow.process.FlowExecutionExchangePostProcessor;
import io.pipelite.core.flow.process.FlowExecutionExchangePreProcessor;
import io.pipelite.core.flow.route.ReturnAddressRouterNode;
import io.pipelite.core.support.LogUtils;
import io.pipelite.dsl.definition.EndpointDefinition;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.definition.ProcessorDefinition;
import io.pipelite.dsl.definition.SourceDefinition;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;
import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.FlowFactoryPostProcessor;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import io.pipelite.spi.flow.exchange.FlowNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class FlowFactory {

    private final PipeliteContext context;
    private final EndpointFactory endpointFactory;

    private final Map<Class<? extends FlowFactoryPostProcessor>, FlowFactoryPostProcessor> flowFactoryPostProcessors;

    public FlowFactory(PipeliteContext context) {
        Objects.requireNonNull(context, "context is required and cannot be null");
        this.context = context;
        endpointFactory = context.getEndpointFactory();
        flowFactoryPostProcessors = new ConcurrentHashMap<>();
    }

    @Deprecated
    public FlowFactory(PipeliteContext context, ExchangeFactory exchangeFactory) {
        this(context);
    }

    public void addPostProcessor(FlowFactoryPostProcessor postProcessor) {

        if (!flowFactoryPostProcessors.containsKey(postProcessor.getClass())) {
            flowFactoryPostProcessors.put(postProcessor.getClass(), postProcessor);
        }

    }

    public Flow createFlow(FlowDefinition flowDefinition) {

        Objects.requireNonNull(flowDefinition, "flowDefinition is required and cannot be null");

        final String flowName = flowDefinition.getFlowName();
        final SourceDefinition sourceDefinition = flowDefinition.sourceDefinition();
        final Endpoint sourceEndpoint = endpointFactory.createEndpoint(sourceDefinition);
        final EndpointURL sourceEndpointURL = sourceEndpoint.getEndpointURL();

        injectDependencies(sourceEndpoint);

        final ExceptionHandler exceptionHandler = flowDefinition.getExceptionHandler(ExceptionHandler.class);
        /*
         * Create consumer
         */

        int sequenceNumber = 0;

        Consumer consumer = sourceEndpoint.createConsumer();
        final String consumerTag = LogUtils.formatTag(flowName, sourceDefinition.getFormattedUrl());
        consumer.setFlowName(flowName);
        consumer.setSourceEndpointResource(sourceEndpointURL.getResource());
        consumer.setProcessorName(sourceEndpointURL.getResource());
        consumer.setSequenceNumber(sequenceNumber++);
        consumer.tag(consumerTag);
        consumer.setExceptionHandler(exceptionHandler);

        injectDependencies(consumer);
        setExchangePrePostProcessors(consumer);

        consumer = postProcessFlowNode(consumer, flowName, sourceEndpointURL.getResource());

        Iterator<ProcessorDefinition> processorsIterator = flowDefinition.iterateProcessorDefinitions();
        FlowNode previousProcessor = consumer;

        final EndpointDefinition endpointDefinition = flowDefinition.endpointDefinition();

        while (processorsIterator.hasNext()) {

            ProcessorDefinition processorDefinition = processorsIterator.next();
            FlowNode processor = processorDefinition.getProcessor(FlowNode.class);

            Preconditions.notNull(processor, String.format("Invalid ProcessorDefinition [%s], processor is required!",
                processorDefinition.getProcessorName()));

            processor.setFlowName(flowName);
            processor.setSourceEndpointResource(sourceEndpointURL.getResource());
            processor.setProcessorName(processorDefinition.getProcessorName());
            processor.setSequenceNumber(sequenceNumber++);
            processor.setExceptionHandler(exceptionHandler);
            final String processorTag = LogUtils.formatTag(flowName, processorDefinition.getProcessorName());
            processor.tag(processorTag);

            injectDependencies(processor);
            setExchangePrePostProcessors(processor);

            if(processor instanceof AbstractProcessorNode processorNode){
                processorNode.wrapDelegate(delegate -> postProcessDelegate(delegate, flowName, processorDefinition.getProcessorName()));
            }

            if (!processorsIterator.hasNext() && endpointDefinition == null) {
                // If it's last processor and there is no sinkEndpoint
                // then add Return-Address router proxy
                processor = new ReturnAddressRouterNode(processor);
                injectDependencies(processor);
            }

            previousProcessor.setNext(processor);
            previousProcessor = processor;
        }

        if (endpointDefinition != null) {
            final Endpoint toEndpoint = endpointFactory.createEndpoint(endpointDefinition);
            final EndpointURL toEndpointURL = toEndpoint.getEndpointURL();

            Producer producer = toEndpoint.createProducer();

            producer.setFlowName(flowName);
            producer.setSourceEndpointResource(sourceEndpointURL.getResource());
            producer.setProcessorName(toEndpointURL.getResource());
            producer.setSequenceNumber(sequenceNumber++);
            producer.setExceptionHandler(exceptionHandler);

            injectDependencies(producer);
            setExchangePrePostProcessors(producer);

            final String producerTag = LogUtils.formatTag(flowName, endpointDefinition.getFormattedUrl());
            producer.tag(producerTag);

            producer = postProcessFlowNode(producer, flowName, toEndpointURL.getResource());

            Objects.requireNonNull(previousProcessor, "previousProcessor is required here!");
            previousProcessor.setNext(producer);
        }

        return new Flow(flowName, sourceDefinition.getUrl(), sourceEndpoint, consumer);
    }

    private void injectDependencies(Object flowNode) {

        if (flowNode instanceof ExchangeFactoryAware) {
            ((ExchangeFactoryAware) flowNode).setExchangeFactory(context.getExchangeFactory());
        }
        if (flowNode instanceof PipeliteContextAware) {
            ((PipeliteContextAware) flowNode).setPipeliteContext(context);
        }

    }

    private Collection<FlowFactoryPostProcessor> sortedFlowNodePostProcessor() {
        return flowFactoryPostProcessors
            .values()
            .stream()
            .sorted(Comparator.comparing(FlowFactoryPostProcessor::getOrder).reversed())
            .toList();
    }

    private <T extends FlowNode> T postProcessFlowNode(T flowNode, String flowName, String processorName) {
        final AtomicReference<T> postProcessed = new AtomicReference<>(flowNode);
        sortedFlowNodePostProcessor()
            .forEach(postProcessor -> postProcessed.set(postProcessor.postProcess(flowNode, flowName, processorName)));
        return postProcessed.get();
    }

    private Processor postProcessDelegate(Processor delegate, String flowName, String processorName) {
        final AtomicReference<Processor> postProcessed = new AtomicReference<>(delegate);
        sortedFlowNodePostProcessor()
            .forEach(postProcessor -> postProcessed.set(postProcessor.postProcess(postProcessed.get(), flowName, processorName)));
        return postProcessed.get();
    }

    private void setExchangePrePostProcessors(FlowNode flowNode) {
        flowNode.addExchangePreProcessor(new FlowExecutionExchangePreProcessor());
        flowNode.addExchangePostProcessor(new FlowExecutionExchangePostProcessor());
    }

}
