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
package io.pipelite.core.flow.internal;

import io.pipelite.core.channel.queue.QueueEndpoint;
import io.pipelite.core.consumer.internal.EventDrivenConsumerService;
import io.pipelite.core.flow.barrier.BarrierExecutionObserver;
import io.pipelite.core.flow.barrier.BarrierCoordinator;
import io.pipelite.core.context.internal.InternalPipeliteContext;

import io.pipelite.core.context.EndpointFactory;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.flow.FlowNodeConfigurer;
import io.pipelite.core.flow.process.FlowExecutionExchangePostProcessor;
import io.pipelite.core.flow.process.FlowExecutionExchangePreProcessor;
import io.pipelite.core.flow.route.FlowExitNode;
import io.pipelite.core.flow.route.RouteNodeFactory;
import io.pipelite.core.support.LogUtils;
import io.pipelite.dsl.definition.EndpointDefinition;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.definition.ProcessorDefinition;
import io.pipelite.dsl.definition.SourceDefinition;
import io.pipelite.core.source.internal.ConsumerEndpoint;
import io.pipelite.core.source.internal.SourceConsumers;
import io.pipelite.core.context.internal.ChannelURL;
import io.pipelite.core.consumer.internal.Consumer;
import io.pipelite.core.consumer.internal.DurableInboxAware;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;
import io.pipelite.spi.source.Source;
import io.pipelite.dsl.process.ExceptionHandler;
import io.pipelite.core.flow.internal.Flow;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.flow.internal.FlowNode;
import io.pipelite.core.flow.execution.inbox.DurableInbox;
import io.pipelite.core.flow.execution.inbox.DurableInboxProperties;
import io.pipelite.core.flow.execution.inbox.NoOpDurableInbox;

import java.util.Iterator;
import java.util.Objects;

public class FlowFactory {

    private final InternalPipeliteContext context;
    private static final String DEFAULT_SOURCE_ROLE = "event";

    private final EndpointFactory endpointFactory;

    /** Called with every producer an adapter creates for a flow, so the context can close it when it stops. */
    private final java.util.function.Consumer<AutoCloseable> producerRegistrar;

    public FlowFactory(InternalPipeliteContext context){
        this(context, producer -> { });
    }

    public FlowFactory(InternalPipeliteContext context, java.util.function.Consumer<AutoCloseable> producerRegistrar){
        Objects.requireNonNull(context, "context is required and cannot be null");
        Objects.requireNonNull(producerRegistrar, "producerRegistrar is required and cannot be null");
        this.context = context;
        this.producerRegistrar = producerRegistrar;
        endpointFactory = context.getEndpointFactory();
    }

    @Deprecated
    public FlowFactory(InternalPipeliteContext context, ExchangeFactory exchangeFactory){
        this(context);
    }

    public Flow createFlow(FlowDefinition flowDefinition){

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
        final Consumer consumer = createConsumer(sourceEndpoint, sourceDefinition);
        final String consumerTag = LogUtils.formatTag(flowName, sourceDefinition.getFormattedUrl());
        consumer.setFlowName(flowName);
        consumer.setSourceEndpointResource(sourceEndpointURL.getResource());
        consumer.setProcessorName(sourceEndpointURL.getResource());
        consumer.tag(consumerTag);
        consumer.setExceptionHandler(exceptionHandler);
        injectDependencies(consumer);
        wireDurableInbox(consumer, flowName, sourceEndpointURL);
        wireExecutionObserver(consumer, sourceEndpoint, sourceEndpointURL);
        setPrePostProcessors(consumer);

        Iterator<ProcessorDefinition> processorsIterator = flowDefinition.iterateProcessorDefinitions();
        FlowNode previousProcessor = consumer;

        final EndpointDefinition endpointDefinition = flowDefinition.endpointDefinition();

        while(processorsIterator.hasNext()){

            ProcessorDefinition processorDefinition = processorsIterator.next();
            FlowNode processor = processorDefinition.getProcessor(FlowNode.class);
            Objects.requireNonNull(processor, String.format("Invalid ProcessorDefinition [%s], processor is required!",
                processorDefinition.getProcessorName()));

            processor.setFlowName(flowName);
            processor.setSourceEndpointResource(sourceEndpointURL.getResource());
            processor.setProcessorName(processorDefinition.getProcessorName());
            processor.setExceptionHandler(exceptionHandler);
            final String processorTag = LogUtils.formatTag(flowName, processorDefinition.getProcessorName());
            processor.tag(processorTag);

            injectDependencies(processor);
            setPrePostProcessors(processor);

            if(!processorsIterator.hasNext() && processor instanceof FlowExitNode){
                // The flow's own exit (toRoute/toRecipientList): a routing slip with routes
                // left is followed instead of it
                previousProcessor = appendRoutingSlipGate(previousProcessor, RouteNodeFactory.routingSlipGate(),
                    flowName, sourceEndpointURL, exceptionHandler);
            }

            previousProcessor.setNext(processor);
            previousProcessor = processor;
        }

        if(endpointDefinition != null){
            final Endpoint toEndpoint = endpointFactory.createEndpoint(endpointDefinition);
            final EndpointURL toEndpointURL = toEndpoint.getEndpointURL();

            // The adapter's Producer knows nothing of flows: it is wrapped in the node that ends the chain.
            final Producer adapterProducer = toEndpoint.createProducer();
            producerRegistrar.accept(adapterProducer);
            final ProducerNode producer = new ProducerNode(adapterProducer);

            producer.setFlowName(flowName);
            producer.setSourceEndpointResource(sourceEndpointURL.getResource());
            producer.setProcessorName(toEndpointURL.getResource());
            producer.setExceptionHandler(exceptionHandler);

            final String producerTag = LogUtils.formatTag(flowName, endpointDefinition.getFormattedUrl());
            producer.tag(producerTag);

            Objects.requireNonNull(previousProcessor, "previousProcessor is required here!");
            previousProcessor = appendRoutingSlipGate(previousProcessor, RouteNodeFactory.routingSlipGate(),
                flowName, sourceEndpointURL, exceptionHandler);
            previousProcessor.setNext(producer);
        } else if(!(previousProcessor instanceof FlowExitNode)){
            // No exit of its own: the end of the flow follows a routing slip and, once it is
            // exhausted, replies to the return address
            appendRoutingSlipGate(previousProcessor, RouteNodeFactory.endOfFlowGate(),
                flowName, sourceEndpointURL, exceptionHandler);
        }

        return new Flow(flowName, sourceEndpointURL.getResource(), sourceEndpoint, consumer);
    }

    /**
     * Named and wired like a processor so a failed hop is reported through the flow's exception
     * handler and a retry can resume right at the gate (see {@code FlowNodeLocator}).
     */
    private FlowNode appendRoutingSlipGate(FlowNode previous, FlowNode gate, String flowName,
                                           EndpointURL sourceEndpointURL, ExceptionHandler exceptionHandler){
        gate.setFlowName(flowName);
        gate.setSourceEndpointResource(sourceEndpointURL.getResource());
        gate.setProcessorName(RouteNodeFactory.routingSlipGateName());
        gate.setExceptionHandler(exceptionHandler);
        injectDependencies(gate);
        previous.setNext(gate);
        return gate;
    }

    /**
     * The consumer that starts the flow. An adapter provides a {@link Source}, which is wrapped in the
     * framework's own consumer; the endpoints of the framework itself (the queue, the retry channel)
     * provide their consumer directly.
     */
    private Consumer createConsumer(Endpoint sourceEndpoint, SourceDefinition sourceDefinition) {
        if (sourceEndpoint instanceof ConsumerEndpoint) {
            return ((ConsumerEndpoint) sourceEndpoint).createConsumer();
        }
        final Source source = sourceEndpoint.createSource();
        return SourceConsumers.wrap(source, sourceEndpoint, roleOf(sourceDefinition));
    }

    /** Names the threads of a source after its protocol: kafka, time, file... */
    private static String roleOf(SourceDefinition sourceDefinition) {
        // getUrl(), not getFormattedUrl(): the latter is lower-cased and loses its slashes, for the log tag.
        final ChannelURL channelURL = ChannelURL.parse(sourceDefinition.getUrl());
        return channelURL.hasProtocol() ? channelURL.getProtocol() : DEFAULT_SOURCE_ROLE;
    }

    private void injectDependencies(Object flowNode){
        FlowNodeConfigurer.injectDependencies(flowNode, context);
    }

    /**
     * Only the source consumer needs a {@code DurableInbox} (issue #70) — never a processor or
     * producer further down the chain, since intake/acknowledge only ever happen at the queue
     * boundary a consumer owns — so this is a separate, consumer-only step rather than folded
     * into {@link FlowNodeConfigurer#injectDependencies}, which every node in the chain goes
     * through. Resolved once here, at flow-build time, per this class's own Javadoc on why {@code
     * DurableInbox#enqueue} itself takes no flow-identifying parameter. Keyed by the flow name, not by
     * the resource of the source (issue #108).
     */
    private void wireDurableInbox(Consumer consumer, String flowName, EndpointURL sourceEndpointURL) {
        if (!(consumer instanceof DurableInboxAware)) {
            return;
        }
        final boolean enabled = sourceEndpointURL.getProperties()
            .getAsBooleanOrDefault(DurableInboxProperties.DURABLE_INBOX, true);
        final DurableInbox durableInbox = enabled
            ? context.getDurableInboxProvider().forFlow(flowName)
            : NoOpDurableInbox.INSTANCE;
        ((DurableInboxAware) consumer).setDurableInbox(durableInbox);
    }

    /**
     * The flows that read a queue can be awaited by a barrier (issue #150): their consumer tells the coordinator
     * when an execution ends. Nothing is done for an execution unless some barrier awaits the flow.
     */
    private void wireExecutionObserver(Consumer consumer, Endpoint sourceEndpoint, EndpointURL sourceEndpointURL) {
        if (!(consumer instanceof EventDrivenConsumerService) || !(sourceEndpoint instanceof QueueEndpoint)) {
            return;
        }
        final BarrierCoordinator coordinator = context.getBarrierCoordinator();
        if (coordinator != null) {
            ((EventDrivenConsumerService) consumer).setExecutionObserver(
                new BarrierExecutionObserver(sourceEndpointURL.getResource(), coordinator));
        }
    }

    private void setPrePostProcessors(FlowNode flowNode){
        flowNode.addExchangePreProcessor(new FlowExecutionExchangePreProcessor());
        flowNode.addExchangePostProcessor(new FlowExecutionExchangePostProcessor());
    }
}
