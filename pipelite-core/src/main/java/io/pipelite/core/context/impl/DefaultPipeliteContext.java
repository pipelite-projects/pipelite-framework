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
package io.pipelite.core.context.impl;

import io.pipelite.core.config.DefaultDependencyRegistry;
import io.pipelite.core.config.DependencyRegistry;
import io.pipelite.core.config.EndpointURLPropertyResolver;
import io.pipelite.core.config.FlowConfigurationScanner;
import io.pipelite.core.config.NoOpEndpointURLPropertyResolver;
import io.pipelite.core.context.*;
import io.pipelite.core.flow.DeadLetterChannelExceptionHandler;
import io.pipelite.core.flow.FlowFactory;
import io.pipelite.core.flow.RetryChannelExceptionHandler;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.split.AggregateInMemoryRepository;
import io.pipelite.core.flow.split.AggregateRepository;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpFactory;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.core.flow.execution.retry.RetryChannelDefinitionFactory;
import io.pipelite.core.flow.execution.retry.RetryService;
import io.pipelite.core.support.LogUtils;
import io.pipelite.core.support.serialization.Base64ObjectSerializer;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.definition.SourceDefinition;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelConfigurer;
import io.pipelite.spi.channel.ChannelURL;
import io.pipelite.spi.context.Service;
import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;
import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.concurrent.DefaultThreadFactory;
import io.pipelite.spi.flow.concurrent.SourceConcurrencyProperties;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.MessageFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DefaultPipeliteContext implements ConfigurablePipeliteContext {

    private static final String RETRY_CHANNEL_NAME = "retry-channel";

    private static final int DEFAULT_MAX_SOURCE_WORKER_POOL_SIZE = 200;
    private static final String SOURCE_WORKER_POOL_THREAD_ROLE = "pool";
    private static final long SOURCE_WORKER_POOL_SHUTDOWN_TIMEOUT_SECONDS = 30L;

    private final Logger rootLogger = LogUtils.getRootLogger();
    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final Collection<FlowDefinition> flowDefinitions;

    private final DependencyRegistry dependencyRegistry;
    private final FlowConfigurationScanner flowConfigurationScanner;

    private final FlowRegistry flowRegistry;
    private final ServiceManager serviceManager;
    private final ChannelAdapterManager channelAdapterManager;

    private final DefaultEndpointFactory endpointFactory;
    private final FlowFactory flowFactory;
    private final ExchangeFactory exchangeFactory;
    private final AggregateRepository aggregateRepository;

    private final FlowExecutionDumpRepository executionDumpRepository;
    private final FlowExecutionDumpFactory executionDumpFactory;
    private final RetryChannelDefinitionFactory retryChannelDefinitionFactory;

    private int maxSourceWorkerPoolSize = DEFAULT_MAX_SOURCE_WORKER_POOL_SIZE;
    private ExecutorService sourceWorkerPool;

    public DefaultPipeliteContext() {

        flowDefinitions = new ArrayList<>();

        dependencyRegistry = new DefaultDependencyRegistry();
        flowConfigurationScanner = new FlowConfigurationScanner();

        final MessageFactory messageFactory = new DefaultMessageFactory(new DistributedIdentityGeneratorImpl());
        exchangeFactory = new DefaultExchangeFactory(messageFactory);
        aggregateRepository = new AggregateInMemoryRepository();

        flowRegistry = new DefaultFlowRegistry();
        serviceManager = new DefaultServiceManager();
        channelAdapterManager = new DefaultChannelAdapterManager(exchangeFactory);

        endpointFactory = new DefaultEndpointFactory(channelAdapterManager, new NoOpEndpointURLPropertyResolver());
        flowFactory = new FlowFactory(this);

        executionDumpFactory = new FlowExecutionDumpFactory(new DistributedIdentityGeneratorImpl(), new Base64ObjectSerializer());
        executionDumpRepository = new FlowExecutionDumpInMemoryRepository();
        retryChannelDefinitionFactory = new RetryChannelDefinitionFactory(executionDumpRepository, this);
    }

    @Override
    public void setEndpointURLPropertyResolver(EndpointURLPropertyResolver resolver) {
        endpointFactory.setEndpointURLPropertyResolver(resolver);
    }

    @Override
    public void setMaxSourceWorkerPoolSize(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be a positive integer, got " + size);
        }
        this.maxSourceWorkerPoolSize = size;
    }

    @Override
    public synchronized ExecutorService getSourceWorkerPool() {
        // Lazy, not just eager-in-start(): FlowFactory#createFlow (and tests that call it
        // directly, bypassing the full start() lifecycle) already triggers FlowNodeConfigurer
        // injection, which needs this pool to exist regardless of whether start() has run yet.
        if (sourceWorkerPool == null) {
            // No identity supplier: this pool is shared across every flow with concurrency>1,
            // so its threads get a stable per-pool ordinal ("pipelite-pool-1", "pipelite-pool-2",
            // ...) rather than any single flow's name — EventDrivenConsumerService transiently
            // tags a pool thread's name with whichever flow it's currently executing for.
            sourceWorkerPool = Executors.newFixedThreadPool(maxSourceWorkerPoolSize,
                new DefaultThreadFactory(SOURCE_WORKER_POOL_THREAD_ROLE));
        }
        return sourceWorkerPool;
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
    public AggregateRepository getAggregateRepository() {
        return aggregateRepository;
    }

    @Override
    public void registerFlowDefinition(FlowDefinition flowDefinition) {
        if(isRegistered(flowDefinition.getFlowName())){
            throw new DuplicateFlowDefinitionException(flowDefinition.getFlowName());
        }
        flowDefinitions.add(flowDefinition);
    }

    @Override
    public void registerFlowConfigurationClass(Class<?> configurationClass) {
        flowConfigurationScanner.scan(configurationClass, dependencyRegistry)
            .forEach(this::registerFlowDefinition);
    }

    @Override
    public void registerDependency(String name, Object instance) {
        dependencyRegistry.register(name, instance);
    }

    @Override
    public Optional<FlowDefinition> getFlowDefinition(String flowName) {
        return flowDefinitions
            .stream()
            .filter(fd -> fd.getFlowName().equals(flowName))
            .findFirst();
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

        // create the shared fromSource worker pool first: FlowNodeConfigurer injects it into
        // consumers during registerFlows(), and EventDrivenConsumerService needs it at doStart()
        getSourceWorkerPool();

        // find channelAdapters and register automatically
        channelAdapterManager.scan();

        // register Flow
        registerFlows();

        // diagnostic-only: log the total fromSource concurrency declared across all flows
        logSourceConcurrencyGuardrail();

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

        // shut down the shared fromSource worker pool: bounded wait for in-flight tasks,
        // then force-cancel if the timeout elapses rather than leaking threads indefinitely
        if (sourceWorkerPool != null) {
            sourceWorkerPool.shutdown();
            try {
                if (!sourceWorkerPool.awaitTermination(SOURCE_WORKER_POOL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    if (sysLogger.isWarnEnabled()) {
                        sysLogger.warn("sourceWorkerPool did not terminate within {}s, forcing shutdown", SOURCE_WORKER_POOL_SHUTDOWN_TIMEOUT_SECONDS);
                    }
                    sourceWorkerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                sourceWorkerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // notify context-stopped event
        channelAdapterManager.notifyContextStopped();

        if(sysLogger.isDebugEnabled()){
            sysLogger.debug("PipeliteContext stopped");
        }
    }

    /**
     * Diagnostic-only: sums the {@code concurrency} declared by every registered flow's
     * {@code fromSource} (only meaningful for no-protocol/internal sources — see
     * {@link DefaultEndpointFactory}, which is the actual enforcement point for
     * channel-adapter-backed sources) and logs the total against the shared pool size.
     * Never throws — a flow whose source URL can't be resolved here (e.g. an unresolved
     * {@code ${...}} placeholder) is simply treated as {@code concurrency=1} for this log;
     * real validation happens later, in {@code DefaultEndpointFactory#createEndpoint}.
     */
    private void logSourceConcurrencyGuardrail() {
        int total = 0;
        final StringBuilder perFlow = new StringBuilder();
        for (FlowDefinition flowDefinition : flowDefinitions) {
            final int concurrency = resolveDeclaredConcurrency(flowDefinition.sourceDefinition());
            total += concurrency;
            if (concurrency > 1) {
                if (!perFlow.isEmpty()) {
                    perFlow.append(", ");
                }
                perFlow.append(flowDefinition.getFlowName()).append('=').append(concurrency);
            }
        }
        if (sysLogger.isInfoEnabled()) {
            sysLogger.info("{} flow(s) registered; total fromSource worker threads across the shared pool: {} (pool size: {}){}",
                flowDefinitions.size(), total, maxSourceWorkerPoolSize,
                !perFlow.isEmpty() ? " [" + perFlow + "]" : "");
        }
        if (total > maxSourceWorkerPoolSize && sysLogger.isWarnEnabled()) {
            sysLogger.warn("Sum of declared fromSource concurrency ({}) exceeds the shared source worker pool size ({}) — " +
                    "flows will compete for the shared budget under simultaneous load and may not always reach their declared " +
                    "concurrency. Configure a larger pool via ConfigurablePipeliteContext#setMaxSourceWorkerPoolSize if this is not expected.",
                total, maxSourceWorkerPoolSize);
        }
    }

    private int resolveDeclaredConcurrency(SourceDefinition sourceDefinition) {
        try {
            final ChannelURL channelURL = ChannelURL.parse(sourceDefinition.getUrl());
            if (channelURL.hasProtocol()) {
                // channel-adapter-backed sources don't support concurrency in v1 —
                // DefaultEndpointFactory#createEndpoint is the real enforcement point.
                return 1;
            }
            return EndpointURL.parse(channelURL.getEndpointURL())
                .getProperties()
                .getAsIntegerOrDefault(SourceConcurrencyProperties.CONCURRENCY, 1);
        } catch (RuntimeException e) {
            return 1;
        }
    }

    @Override
    public Optional<Flow> tryFindFlow(String sourceEndpointResource) {
        return flowRegistry.tryFindFlow(sourceEndpointResource);
    }

    @Override
    public Optional<Flow> tryFindFlowByName(String flowName) {
        return flowRegistry.tryFindFlowByName(flowName);
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

            } else {
                // No retry channel: a bare dead-letter channel (.withErrorChannel(c ->
                // c.definedFlow(flowName)) with no .withRetryChannel(...)) routes on the very
                // first failure and needs this context to dispatch there — see
                // DeadLetterChannelExceptionHandler. Fetched as the base ExceptionHandler type
                // (never throws) rather than FlowDefinition#getExceptionHandler(specific-class),
                // which throws ClassCastException for any other handler type a flow might carry
                // (e.g. PipeliteTestFixture's own capture handler) instead of just not matching.
                final ExceptionHandler configuredHandler = flowDefinition.getExceptionHandler(ExceptionHandler.class);
                if(configuredHandler instanceof DeadLetterChannelExceptionHandler){
                    ((DeadLetterChannelExceptionHandler) configuredHandler).setPipeliteContext(this);
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
