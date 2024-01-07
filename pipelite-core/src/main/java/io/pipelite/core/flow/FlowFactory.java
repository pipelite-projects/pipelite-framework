package io.pipelite.core.flow;

import io.pipelite.core.context.EndpointFactory;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.flow.process.FlowExecutionExchangePostProcessor;
import io.pipelite.core.flow.process.FlowExecutionExchangePreProcessor;
import io.pipelite.core.flow.route.ReturnAddressRouterNode;
import io.pipelite.core.support.LogUtils;
import io.pipelite.dsl.definition.EndpointDefinition;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.definition.ProcessorDefinition;
import io.pipelite.dsl.definition.SourceDefinition;
import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;
import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import io.pipelite.spi.flow.exchange.FlowNode;

import java.util.Iterator;
import java.util.Objects;

public class FlowFactory {

    private final PipeliteContext context;
    private final EndpointFactory endpointFactory;

    public FlowFactory(PipeliteContext context){
        Objects.requireNonNull(context, "context is required and cannot be null");
        this.context = context;
        endpointFactory = context.getEndpointFactory();
    }

    @Deprecated
    public FlowFactory(PipeliteContext context, ExchangeFactory exchangeFactory){
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
        final Consumer consumer = sourceEndpoint.createConsumer();
        final String consumerTag = LogUtils.formatTag(flowName, sourceDefinition.getFormattedUrl());
        consumer.setFlowName(flowName);
        consumer.setSourceEndpointResource(sourceEndpointURL.getResource());
        consumer.setProcessorName(sourceEndpointURL.getResource());
        consumer.tag(consumerTag);
        consumer.setExceptionHandler(exceptionHandler);
        injectDependencies(consumer);
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

            if(!processorsIterator.hasNext() && endpointDefinition == null){
                // If it's last processor and there is no sinkEndpoint
                // then add Return-Address router proxy
                processor = new ReturnAddressRouterNode(processor);
                injectDependencies(processor);
            }

            previousProcessor.setNext(processor);
            previousProcessor = processor;
        }

        if(endpointDefinition != null){
            final Endpoint toEndpoint = endpointFactory.createEndpoint(endpointDefinition);
            final EndpointURL toEndpointURL = toEndpoint.getEndpointURL();

            final Producer producer = toEndpoint.createProducer();

            producer.setFlowName(flowName);
            producer.setSourceEndpointResource(sourceEndpointURL.getResource());
            producer.setProcessorName(toEndpointURL.getResource());
            producer.setExceptionHandler(exceptionHandler);

            final String producerTag = LogUtils.formatTag(flowName, endpointDefinition.getFormattedUrl());
            producer.tag(producerTag);

            Objects.requireNonNull(previousProcessor, "previousProcessor is required here!");
            previousProcessor.setNext(producer);
        }

        return new Flow(flowName, sourceDefinition.getUrl(), sourceEndpoint, consumer);
    }

    private void injectDependencies(Object flowNode){

        if(flowNode instanceof ExchangeFactoryAware){
            ((ExchangeFactoryAware)flowNode).setExchangeFactory(context.getExchangeFactory());
        }
        if(flowNode instanceof PipeliteContextAware){
            ((PipeliteContextAware)flowNode).setPipeliteContext(context);
        }

    }

    private void setPrePostProcessors(FlowNode flowNode){
        flowNode.addExchangePreProcessor(new FlowExecutionExchangePreProcessor());
        flowNode.addExchangePostProcessor(new FlowExecutionExchangePostProcessor());
    }
}
