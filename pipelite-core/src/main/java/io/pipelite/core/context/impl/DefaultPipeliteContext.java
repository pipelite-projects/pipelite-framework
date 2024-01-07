package io.pipelite.core.context.impl;

import io.pipelite.core.context.*;
import io.pipelite.core.flow.FlowFactory;
import io.pipelite.core.flow.RetryChannelExceptionHandler;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpFactory;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.core.flow.execution.retry.RetryChannelDefinitionFactory;
import io.pipelite.core.flow.execution.retry.RetryService;
import io.pipelite.core.support.LogUtils;
import io.pipelite.core.support.serialization.Base64ObjectSerializer;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelConfigurer;
import io.pipelite.spi.channel.ChannelURL;
import io.pipelite.spi.context.Service;
import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.MessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class DefaultPipeliteContext implements PipeliteContext {

    private static final String RETRY_CHANNEL_NAME = "retry-channel";

    private final Logger rootLogger = LogUtils.getRootLogger();
    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final Collection<FlowDefinition> flowDefinitions;

    private final FlowRegistry flowRegistry;
    private final ServiceManager serviceManager;
    private final ChannelAdapterManager channelAdapterManager;

    private final EndpointFactory endpointFactory;
    private final FlowFactory flowFactory;
    private final ExchangeFactory exchangeFactory;

    private final FlowExecutionDumpRepository executionDumpRepository;
    private final FlowExecutionDumpFactory executionDumpFactory;
    private final RetryChannelDefinitionFactory retryChannelDefinitionFactory;

    public DefaultPipeliteContext() {

        flowDefinitions = new ArrayList<>();

        final MessageFactory messageFactory = new DefaultMessageFactory(new DistributedIdentityGeneratorImpl());
        exchangeFactory = new DefaultExchangeFactory(messageFactory);

        flowRegistry = new DefaultFlowRegistry();
        serviceManager = new DefaultServiceManager();
        channelAdapterManager = new DefaultChannelAdapterManager(exchangeFactory);

        endpointFactory = new DefaultEndpointFactory(channelAdapterManager);
        flowFactory = new FlowFactory(this);

        executionDumpFactory = new FlowExecutionDumpFactory(new DistributedIdentityGeneratorImpl(), new Base64ObjectSerializer());
        executionDumpRepository = new FlowExecutionDumpInMemoryRepository();
        retryChannelDefinitionFactory = new RetryChannelDefinitionFactory(executionDumpRepository);
    }

    @Deprecated
    public DefaultPipeliteContext(ExchangeFactory exchangeFactory) {
        this();
    }

    @Override
    public EndpointFactory getEndpointFactory() {
        return endpointFactory;
    }

    @Override
    public ExchangeFactory getExchangeFactory() {
        return exchangeFactory;
    }

    @Override
    public void registerFlowDefinition(FlowDefinition flowDefinition) {
        if(!flowDefinitions.contains(flowDefinition)){
            flowDefinitions.add(flowDefinition);
        }
    }

    @Override
    public void addChannelConfigurer(ChannelConfigurer<?> configurer) {
        channelAdapterManager.addChannelConfigurer(configurer);
    }

    @Override
    public boolean isRegistered(String flowName) {
        return flowDefinitions
            .stream()
            .anyMatch(fd -> fd.getFlowName()
                .equals(flowName));
    }

    @Override
    public void start() {

        // find channelAdapters and register automatically
        channelAdapterManager.scan();

        // register Flow
        registerFlows();

        // start services
        serviceManager.startServices();

        // notify context-started event
        channelAdapterManager.notifyContextStarted();

        if(sysLogger.isDebugEnabled()){
            sysLogger.debug("PipeliteContext started");
        }

        //if(rootLogger.isInfoEnabled()){
            //rootLogger.info("Pipelite v1ersion {}", Package.getPackage("io.pipelite").getImplementationVersion());
        //}
    }

    @Override
    public void stop() {

        // stop services
        serviceManager.stopServices();

        // notify context-stopped event
        channelAdapterManager.notifyContextStopped();

        if(sysLogger.isDebugEnabled()){
            sysLogger.debug("PipeliteContext stopped");
        }
    }

    @Override
    public void supplyExchange(String destinationURL, Exchange exchange) {

        final ChannelURL channelURL = ChannelURL.parse(destinationURL);
        if(channelURL.hasProtocol()){
            final Optional<ChannelAdapter> channelHolder = channelAdapterManager.tryResolveChannel(channelURL.getProtocol());
            if(channelHolder.isPresent()){
                // create producer and produce exchange
                final ChannelAdapter channel = channelHolder.get();
                final Endpoint endpoint = channel.createEndpoint(channelURL.getEndpointURL());
                final Producer producer = endpoint.createProducer();
                producer.process(exchange);
                return;
            } else {
                throw new IllegalArgumentException(String.format("Unrecognized destination '%s', unable to supply exchange", destinationURL));
            }
        }

        final EndpointURL endpointURL = EndpointURL.parse(channelURL.getEndpointURL());
        final String sourceEndpointURI = endpointURL.getResource();

        final Optional<Flow> flowHolder = flowRegistry.tryFindFlow(sourceEndpointURI);
        if(flowHolder.isPresent()){
            final Flow destination = flowHolder.get();
            final Consumer consumer = destination.getConsumerAs(Consumer.class);
            consumer.consume(exchange);
        }else{
            throw new IllegalArgumentException(String.format("Unrecognized destination '%s', unable to supply exchange", destinationURL));
        }
    }



    private void registerFlows() {

        // for each FlowDefinition create a Flow and add to FlowRegistry
        flowDefinitions.forEach(flowDefinition -> {

            // If retry-channel is configured
            if(flowDefinition.isRetryable()){

                // Inject dependencies on RetryChannelExceptionHandler
                final RetryChannelExceptionHandler exceptionHandler = flowDefinition.getExceptionHandler(RetryChannelExceptionHandler.class);
                exceptionHandler.setExecutionDumpFactory(executionDumpFactory);
                exceptionHandler.setDumpRepository(executionDumpRepository);

                // Create the retry-channel if not already done
                if(!flowRegistry.isRegistered(RETRY_CHANNEL_NAME)){
                    final FlowDefinition retryChannelDefinition = retryChannelDefinitionFactory.createDefinition(RETRY_CHANNEL_NAME);
                    final Flow retryChannel = flowFactory.createFlow(retryChannelDefinition);
                    flowRegistry.addFlow(retryChannel);

                    final RetryService retryService = retryChannel.getConsumerAs(RetryService.class);
                    retryService.setFlowExecutionDumpRepository(executionDumpRepository);

                    serviceManager.registerService(retryService);

                }

            }

            // Create and register the flow
            final Flow flow = flowFactory.createFlow(flowDefinition);
            flowRegistry.addFlow(flow);

            // If Flow's Consumer is a Service then register it on ServiceManager
            if(flow.isConsumerOfType(Service.class)){
                final Service service = flow.getConsumerAs(Service.class);
                serviceManager.registerService(service);
            }
            channelAdapterManager.notifyFlowRegisterd(flow);
        });

    }

}
