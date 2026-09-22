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

import io.pipelite.common.support.Preconditions;
import io.pipelite.common.support.fs.PipeliteHome;
import io.pipelite.core.config.DefaultDependencyRegistry;
import io.pipelite.core.config.DependencyRegistry;
import io.pipelite.core.config.EndpointURLPropertyResolver;
import io.pipelite.core.config.FlowConfigurationScanner;
import io.pipelite.core.config.NoOpEndpointURLPropertyResolver;
import io.pipelite.core.context.*;
import io.pipelite.core.context.internal.DestinationURLs;
import io.pipelite.core.context.internal.ReservedNames;
import io.pipelite.core.context.internal.validation.ContextValidatorChain;
import io.pipelite.core.context.internal.validation.FlowReferenceValidator;
import io.pipelite.core.context.internal.validation.QueueSourceUniquenessValidator;
import io.pipelite.core.context.internal.validation.ReservedFlowNameValidator;
import io.pipelite.core.context.internal.validation.ContextValidator;
import io.pipelite.core.context.internal.validation.ValidationContext;
import io.pipelite.core.flow.DeadLetterChannelExceptionHandler;
import io.pipelite.core.flow.internal.FlowFactory;
import io.pipelite.core.flow.RetryChannelExceptionHandler;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.deadletter.DeadLetterQueueExceptionHandler;
import io.pipelite.core.flow.execution.deadletter.DeadLetterQueueRepository;
import io.pipelite.core.flow.execution.deadletter.DeadLetteredExchangeFactory;
import io.pipelite.core.flow.execution.deadletter.FileDeadLetterQueueRepository;
import io.pipelite.core.flow.split.AggregateInMemoryRepository;
import io.pipelite.core.flow.split.AggregateRepository;
import io.pipelite.core.flow.execution.dump.FileFlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpFactory;
import io.pipelite.core.flow.execution.inbox.DurableInboxDeadLetterWriter;
import io.pipelite.core.flow.execution.inbox.FileDurableInboxDeadLetterWriter;
import io.pipelite.core.flow.execution.retry.internal.RetryChannelDefinitionFactory;
import io.pipelite.core.flow.execution.retry.internal.RetryService;
import io.pipelite.core.support.LogUtils;
import io.pipelite.common.support.serialization.Base64ObjectSerializer;
import io.pipelite.common.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.dsl.ChannelProtocols;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.definition.SourceDefinition;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelConfigurer;
import io.pipelite.spi.channel.ChannelURL;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.context.Service;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;
import io.pipelite.dsl.process.ExceptionHandler;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.concurrent.DefaultThreadFactory;
import io.pipelite.spi.flow.concurrent.SourceConcurrencyProperties;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.MessageFactory;
import io.pipelite.spi.inbox.DurableInbox;
import io.pipelite.spi.inbox.DurableInboxProvider;
import io.pipelite.spi.inbox.InboxEntry;
import io.pipelite.spi.inbox.SegmentedLogDurableInboxProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DefaultPipeliteContext implements ConfigurablePipeliteContext {

    // Namespaced under a dedicated "state/" prefix, sibling to (never inside) any channel
    // adapter's own PipeliteHome subfolder (e.g. "file-channel-adapter") - reserved for the
    // core framework's own durable state, leaving room for future concerns of the same kind
    // (e.g. issue #53's split/aggregate state) without ever colliding with an adapter's folder
    // or the PipeliteHome root itself. See issue #68.
    private static final String FLOW_EXECUTION_DUMPS_HOME_SUBFOLDER = "state/retry";

    // Same "state/" namespacing convention as FLOW_EXECUTION_DUMPS_HOME_SUBFOLDER above, its own
    // sibling subfolder (issue #70) — each source resource gets its own further subdirectory
    // under this one, named by SegmentedLogDurableInboxProvider (see its own Javadoc).
    private static final String DURABLE_INBOX_HOME_SUBFOLDER = "state/inbox";

    // Same "state/" namespacing convention as the two subfolders above, its own sibling
    // subfolder (issue #93) - one file per dead-lettered exchange.
    private static final String DEAD_LETTER_QUEUE_HOME_SUBFOLDER = "state/dlq";

    private static final int DEFAULT_MAX_SOURCE_WORKER_POOL_SIZE = 200;
    private static final String SOURCE_WORKER_POOL_THREAD_ROLE = "pool";
    private static final long SOURCE_WORKER_POOL_SHUTDOWN_TIMEOUT_SECONDS = 30L;

    private final Logger rootLogger = LogUtils.getRootLogger();
    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final Collection<FlowDefinition> flowDefinitions;

    // The startup validation phase (issue #88) and the read-only view of this context it runs over.
    private final ContextValidatorChain contextValidatorChain;

    private final ValidationContext validationContext = new ValidationContext() {
        @Override
        public Collection<FlowDefinition> flowDefinitions() {
            return Collections.unmodifiableCollection(flowDefinitions);
        }

        @Override
        public String resolveURL(String rawURL) {
            return endpointFactory.resolveURL(rawURL);
        }
    };

    private final DependencyRegistry dependencyRegistry;
    private final FlowConfigurationScanner flowConfigurationScanner;

    private final FlowRegistry flowRegistry;
    private final ServiceManager serviceManager;
    private final ChannelAdapterManager channelAdapterManager;

    // Whether the built-in retry channel has been created yet, tracked here rather than asked of
    // flowRegistry (issue #116): flowRegistry.isRegistered(ReservedNames.RETRY_CHANNEL) answered
    // "does any flow read a source whose resource is 'retry-channel'", not "has the retry channel
    // already been created" - a user flow on queue://retry-channel or even time://retry-channel,
    // registered before the first retryable flow, made the answer yes and silently skipped creating
    // it. A source resource is an address, not an identity (issue #108); the two must never be
    // confused. ReservedFlowNameValidator rejects that name outright before this ever runs, but
    // this field stays: it is what actually decides whether to create the channel, and does not
    // depend on the validator having run.
    private boolean retryChannelCreated = false;

    private final DefaultEndpointFactory endpointFactory;
    private final FlowFactory flowFactory;
    private final ExchangeFactory exchangeFactory;
    private final AggregateRepository aggregateRepository;

    // Not final: ConfigurablePipeliteContext#setFlowExecutionDumpRepository(...) may replace the
    // default before start() - see registerFlows(), which reads this field live rather than
    // capturing it early (unlike the RetryChannelDefinitionFactory bug this replaced, see #68).
    private FlowExecutionDumpRepository executionDumpRepository;
    private final FlowExecutionDumpFactory executionDumpFactory;

    // Not final, same reasoning as executionDumpRepository above: ConfigurablePipeliteContext#
    // setDurableInboxProvider(...) may replace the default before start().
    private DurableInboxProvider durableInboxProvider;

    // Not final, same reasoning as durableInboxProvider above: ConfigurablePipeliteContext#
    // setDurableInboxDeadLetterWriter(...) may replace the default before start().
    private DurableInboxDeadLetterWriter durableInboxDeadLetterWriter;

    // Not final, same reasoning as executionDumpRepository above: ConfigurablePipeliteContext#
    // setDeadLetterQueueRepository(...) may replace the default before start() (issue #93).
    private DeadLetterQueueRepository deadLetterQueueRepository;
    private final DeadLetteredExchangeFactory deadLetterQueueEntryFactory;

    // Symmetric with EventDrivenConsumer/DefaultPollingConsumer's own ObjectToByteArrayConverter
    // (issue #70's write-through hook) - recovery here is the read-back side of that same,
    // Exchange-agnostic byte[] encoding, not a new format.
    private final ByteArrayToObjectConverter inboxPayloadToExchangeConverter = new ByteArrayToObjectConverter();

    private int maxSourceWorkerPoolSize = DEFAULT_MAX_SOURCE_WORKER_POOL_SIZE;
    private ExecutorService sourceWorkerPool;

    public DefaultPipeliteContext() {

        flowDefinitions = new ArrayList<>();

        contextValidatorChain = new ContextValidatorChain();
        contextValidatorChain.add(new FlowReferenceValidator());
        contextValidatorChain.add(new QueueSourceUniquenessValidator());
        contextValidatorChain.add(new ReservedFlowNameValidator());

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
        // Durable by default (issue #68): a retry-channel dump is a redelivery guarantee, and an
        // in-memory-only implementation quietly breaks that guarantee on the one occasion it
        // matters (a crash while dumps are pending). Callers that genuinely want zero I/O instead
        // (e.g. a short-lived test) can still opt back into FlowExecutionDumpInMemoryRepository
        // via setFlowExecutionDumpRepository(...), same as any other override.
        executionDumpRepository = new FileFlowExecutionDumpRepository(PipeliteHome.resolve(FLOW_EXECUTION_DUMPS_HOME_SUBFOLDER));

        // Default-on, local, single-process durable inbox (issue #70) — protects a message from
        // intake until it reaches a terminal state, closing the gap #68 left open (that only
        // covers a message already routed to a retry channel). setDurableInboxProvider(...) can
        // swap this for a shared/distributed one (e.g. Redis-backed, issue #72).
        durableInboxProvider = new SegmentedLogDurableInboxProvider(
            PipeliteHome.resolve(DURABLE_INBOX_HOME_SUBFOLDER), new DistributedIdentityGeneratorImpl());

        // Default-on, local dead-letter sink for a durable-inbox entry whose payload fails to
        // deserialize during recovery (issue #70) - see recoverPendingInboxEntries(...) below.
        // Same shared directory as durableInboxProvider above, not a separate subfolder - a dead
        // letter's file name (<hash>_dlq, see FileDurableInboxDeadLetterWriter) already can't
        // collide with a live segment's own (<hash>_<seq>.log). setDurableInboxDeadLetterWriter(...)
        // can swap this for something else entirely (a pluggable interface, unlike
        // DurableInbox/DurableInboxProvider - see its own Javadoc).
        durableInboxDeadLetterWriter = new FileDurableInboxDeadLetterWriter(
            PipeliteHome.resolve(DURABLE_INBOX_HOME_SUBFOLDER));

        // Default-on, ready-to-use dead letter queue (issue #93): writes a failed Exchange
        // durably to disk, one file per entry, with no Flow required to receive it.
        // setDeadLetterQueueRepository(...) can swap this for a different implementation.
        deadLetterQueueRepository = new FileDeadLetterQueueRepository(PipeliteHome.resolve(DEAD_LETTER_QUEUE_HOME_SUBFOLDER));
        deadLetterQueueEntryFactory = new DeadLetteredExchangeFactory(new DistributedIdentityGeneratorImpl(), new Base64ObjectSerializer());
    }

    /**
     * Adds a validator to the chain the context runs when it starts (issue #88), after the ones the
     * chain already holds. For the framework's own modules, not for applications: the criteria the
     * context is validated against are not something a user configures, so this is deliberately not
     * on {@code ConfigurablePipeliteContext}. Must be called before {@link #start()}.
     */
    public void addContextValidator(ContextValidator validator) {
        contextValidatorChain.add(validator);
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
    public void setFlowExecutionDumpRepository(FlowExecutionDumpRepository repository) {
        this.executionDumpRepository = Preconditions.notNull(repository, "repository is required and cannot be null");
    }

    @Override
    public void setDurableInboxProvider(DurableInboxProvider provider) {
        this.durableInboxProvider = Preconditions.notNull(provider, "provider is required and cannot be null");
    }

    @Override
    public DurableInboxProvider getDurableInboxProvider() {
        return durableInboxProvider;
    }

    @Override
    public void setDurableInboxDeadLetterWriter(DurableInboxDeadLetterWriter writer) {
        this.durableInboxDeadLetterWriter = Preconditions.notNull(writer, "writer is required and cannot be null");
    }

    @Override
    public void setDeadLetterQueueRepository(DeadLetterQueueRepository repository) {
        this.deadLetterQueueRepository = Preconditions.notNull(repository, "repository is required and cannot be null");
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

        // find channelAdapters and register automatically
        channelAdapterManager.scan();

        // cross-flow validation (issue #88): after the adapters are known and before anything with a
        // side effect - the worker pool, the consumers, the durable inbox recovery that re-dispatches
        // exchanges - so a context that does not fit together fails without having started anything
        contextValidatorChain.validate(validationContext);

        // create the shared fromSource worker pool before registering the flows: FlowNodeConfigurer
        // injects it into consumers during registerFlows(), and EventDrivenConsumerService needs it
        // at doStart()
        getSourceWorkerPool();

        // register Flow
        registerFlows();

        // diagnostic-only: log the total fromSource concurrency declared across all flows
        logSourceConcurrencyGuardrail();

        // start services
        serviceManager.startServices();

        // notify context-started event
        try {
            channelAdapterManager.notifyContextStarted();
        } catch (RuntimeException startFailure) {
            // A ContextEventListener.onContextStarted() failing here must not leave the consumer
            // threads serviceManager.startServices() just started running behind a reported
            // start() failure (issue #54) - the caller sees an exception and reasonably assumes
            // nothing is active. Roll back exactly like a normal stop() would, so that assumption
            // actually holds; a failure during that rollback is attached rather than allowed to
            // replace/hide the real cause.
            try {
                stop();
            } catch (RuntimeException rollbackFailure) {
                startFailure.addSuppressed(rollbackFailure);
            }
            throw startFailure;
        }

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
     * {@code fromSource} (only meaningful for queue sources - see
     * {@link DefaultEndpointFactory}, which is the actual enforcement point for
     * any other source) and logs the total against the shared pool size.
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
            if (!ChannelProtocols.QUEUE.equals(channelURL.getProtocol())) {
                // only a queue has concurrent consumers; another source declaring concurrency is
                // rejected by DefaultEndpointFactory#createEndpoint, the real enforcement point.
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
    public void supplyExchange(String destinationURL, ExchangeImpl exchange) {

        // A destination is a URL (issue #102): a bare name used to fall through to a lookup in the
        // flow registry by source endpoint resource, a second, undocumented way in that is gone.
        final ChannelURL channelURL = DestinationURLs.require(destinationURL);

        final Optional<ChannelAdapter> channelHolder = channelAdapterManager.tryResolveChannel(channelURL.getProtocol());
        if(channelHolder.isEmpty()){
            throw new IllegalArgumentException(String.format("Unrecognized destination '%s', unable to supply exchange", destinationURL));
        }

        // create producer and produce exchange
        final ChannelAdapter channel = channelHolder.get();
        final Endpoint endpoint = channel.createEndpoint(channelURL.getEndpointURL());
        final Producer producer = endpoint.createProducer();
        producer.process(exchange);
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
                if(!retryChannelCreated){
                    // Built here rather than once in the constructor: executionDumpRepository may
                    // have been swapped by setFlowExecutionDumpRepository(...) any time before
                    // start(), and this factory must bake in whatever is current now, not
                    // whatever was current at construction time (see #68).
                    final RetryChannelDefinitionFactory retryChannelDefinitionFactory =
                        new RetryChannelDefinitionFactory(executionDumpRepository, this, deadLetterQueueRepository);
                    final FlowDefinition retryChannelDefinition = retryChannelDefinitionFactory.createDefinition(ReservedNames.RETRY_CHANNEL);
                    final Flow retryChannel = flowFactory.createFlow(retryChannelDefinition);
                    flowRegistry.addFlow(retryChannel);

                    final RetryService retryService = retryChannel.getConsumerAs(RetryService.class);
                    retryService.setFlowExecutionDumpRepository(executionDumpRepository);

                    serviceManager.registerService(retryService);

                    retryChannelCreated = true;
                }

            } else {
                // No retry: a bare error-channel (.withErrorChannel(err -> err.toChannel(...)/
                // err.toDLQ()) with no .withRetry(...)) routes on the very first failure and
                // needs its collaborators injected here. Fetched as the base ExceptionHandler
                // type (never throws) rather than FlowDefinition#getExceptionHandler(specific-
                // class), which throws ClassCastException for any other handler type a flow
                // might carry (e.g. PipeliteTestFixture's own capture handler) instead of just
                // not matching.
                final ExceptionHandler configuredHandler = flowDefinition.getExceptionHandler(ExceptionHandler.class);
                if(configuredHandler instanceof DeadLetterChannelExceptionHandler){
                    ((DeadLetterChannelExceptionHandler) configuredHandler).setPipeliteContext(this);
                } else if(configuredHandler instanceof DeadLetterQueueExceptionHandler){
                    final DeadLetterQueueExceptionHandler dlqHandler = (DeadLetterQueueExceptionHandler) configuredHandler;
                    dlqHandler.setEntryFactory(deadLetterQueueEntryFactory);
                    dlqHandler.setRepository(deadLetterQueueRepository);
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

            // Durable-inbox recovery (issue #70): must happen here, while this flow's consumer
            // still only has entries recovered onto its queue and nothing else - registerFlows()
            // runs entirely before serviceManager.startServices() (see start()), so no dispatch/
            // poll thread has started draining that queue yet, preserving FIFO order between
            // recovered and genuinely new traffic. A no-op for a source that opted out (its
            // DurableInbox is a NoOpDurableInbox with nothing to recover) or the retry-channel's
            // own flow (never reaches this line at all - see the isRetryable() branch above).
            recoverPendingInboxEntries(flow);
        });

    }

    private void recoverPendingInboxEntries(Flow flow) {
        // Keyed by the flow, not by the resource of its source (issue #108): flows that share a resource
        // must not recover each other's entries.
        final String flowName = flow.getName();
        final DurableInbox durableInbox = durableInboxProvider.forFlow(flowName);
        final List<InboxEntry> pending = durableInbox.pendingEntries();
        if (pending.isEmpty()) {
            return;
        }
        if (sysLogger.isInfoEnabled()) {
            sysLogger.info("Recovering {} unacknowledged durable-inbox entr{} for flow '{}'",
                pending.size(), pending.size() == 1 ? "y" : "ies", flowName);
        }
        for (InboxEntry entry : pending) {
            // Isolated per entry (issue #70 follow-up): a payload that fails to deserialize -
            // typically because its class shape changed between the run that wrote it and this
            // one, expected during iterative development - must never abort recovery for every
            // other entry in this flow, let alone every other flow (an unhandled exception here
            // would otherwise propagate out of registerFlows() and prevent the whole context from
            // starting at all, over a single message).
            final ExchangeImpl exchange;
            try {
                exchange = inboxPayloadToExchangeConverter.convert(entry.getPayload(), ExchangeImpl.class);
            } catch (RuntimeException conversionFailure) {
                deadLetterAndAcknowledge(durableInbox, entry, conversionFailure, flowName);
                continue;
            }
            // Tags the resupplied Exchange with its ORIGINAL entry id before it re-enters
            // consume()/process() - both recognize this specific property (see its own Javadoc on
            // why it is distinct from DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME) and resume that entry
            // instead of writing a new, duplicate one that would leave this original entry pending
            // forever.
            exchange.setProperty(IOKeys.DURABLE_INBOX_RESUPPLIED_ENTRY_ID_PROPERTY_NAME, entry.getId());
            flow.supply(exchange);
        }
    }

    private void deadLetterAndAcknowledge(DurableInbox durableInbox, InboxEntry entry,
                                           RuntimeException conversionFailure, String flowName) {
        try {
            durableInboxDeadLetterWriter.write(flowName, entry, conversionFailure);
            // Only acknowledged once safely dead-lettered: otherwise the entry would simply
            // disappear (durableInbox.acknowledge(...) alone, with no dead-letter write, is
            // indistinguishable from silent data loss) - the two must happen together, in this
            // order, or not at all.
            durableInbox.acknowledge(entry.getId());
            if (sysLogger.isErrorEnabled()) {
                sysLogger.error("Durable-inbox entry '{}' for flow '{}' could not be deserialized " +
                        "and has been dead-lettered - metadata: {}",
                    entry.getId(), flowName, entry.getMetadata(), conversionFailure);
            }
        } catch (RuntimeException deadLetterFailure) {
            // Left pending on purpose: better to retry (and fail) the dead-letter write again on
            // the next restart than to lose track of the entry entirely by acknowledging it
            // without ever having safely recorded it anywhere else.
            if (sysLogger.isErrorEnabled()) {
                sysLogger.error("Durable-inbox entry '{}' for flow '{}' failed to deserialize AND " +
                        "could not be dead-lettered - left pending, will be retried on next restart",
                    entry.getId(), flowName, deadLetterFailure);
            }
        }
    }

}
