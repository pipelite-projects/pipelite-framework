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
import io.pipelite.spi.flow.concurrent.ExecutorType;
import io.pipelite.spi.flow.concurrent.FlowNameAbbreviator;
import io.pipelite.spi.flow.concurrent.SourceConcurrencyProperties;
import io.pipelite.spi.flow.concurrent.SourceWorkerPoolAware;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

public class EventDrivenConsumerService extends AbstractService implements Consumer, ExchangeFactoryAware, SourceWorkerPoolAware {

    private static final String DEFAULT_ROLE = "event";
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 30_000L;

    /**
     * Minimum semaphore permits ("in-flight" budget) a {@link PooledDispatchStrategy} dispatcher
     * thread must have available to itself before another dispatcher is added. Found empirically
     * during the issue #49 dispatcher-redesign spike: with fewer permits per dispatcher than this,
     * multiple dispatcher threads thrash contending for too few permits, which costs more in
     * coordination overhead than it buys in parallelism — see
     * {@code 2026-Q3-pipelite-dispatcher-redesign-spike.md} in the pipelite-framework-analysis
     * repository for the numbers (the naive {@code dispatcherCount = min(concurrency, cores)}
     * formula regressed badly at low concurrency; requiring this minimum fixed it).
     */
    static final int MIN_PERMITS_PER_DISPATCHER = 4;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final EventDrivenConsumer eventDrivenConsumer;
    protected final ThreadFactory threadFactory;
    protected ExchangeFactory exchangeFactory;
    protected ExecutorService sourceWorkerPool;

    private DispatchStrategy dispatchStrategy;

    public EventDrivenConsumerService(EventDrivenConsumer eventDrivenConsumer) {
        this(eventDrivenConsumer, DEFAULT_ROLE);
    }

    /**
     * Lets a subclass tied to a specific channel adapter (e.g. {@code KafkaConsumerService})
     * give its threads a more specific role than the generic {@value #DEFAULT_ROLE} — otherwise
     * a Kafka-backed flow's polling thread would be visually indistinguishable from a plain
     * {@code link://}-style flow's dispatch thread(s) in a thread dump.
     */
    protected EventDrivenConsumerService(EventDrivenConsumer eventDrivenConsumer, String role) {
        this.eventDrivenConsumer = eventDrivenConsumer;
        // Lazily resolved: the flow name isn't set on eventDrivenConsumer yet at this point in
        // the flow-building lifecycle (FlowFactory calls setFlowName(...) right after this
        // constructor returns) — but threadFactory.newThread(...) itself is only actually
        // invoked later, in doStart(), by which time it is.
        threadFactory = new DefaultThreadFactory(role, () -> FlowNameAbbreviator.abbreviate(eventDrivenConsumer.getFlowName()));
    }

    @Override
    public void doStart() {

        final EndpointProperties properties = eventDrivenConsumer.getEndpoint().getProperties();
        final int concurrency = properties.getAsIntegerOrDefault(SourceConcurrencyProperties.CONCURRENCY, 1);
        // Validated even though BOUNDED_POOL is the only value today (mirrors TimeUnit.valueOf
        // in ScheduledPollingConsumerService) — an unrecognized value fails fast at startup.
        ExecutorType.valueOf(properties.getOrDefault(SourceConcurrencyProperties.EXECUTOR_TYPE, ExecutorType.BOUNDED_POOL.name()));

        if (dispatchStrategy == null) {
            dispatchStrategy = concurrency <= 1
                ? new InlineDispatchStrategy(eventDrivenConsumer, exchangeFactory, threadFactory, this::isRunAllowed, 1)
                : new PooledDispatchStrategy(eventDrivenConsumer, exchangeFactory, threadFactory, this::isRunAllowed,
                    sourceWorkerPool, dispatcherCountFor(concurrency), concurrency);
        }
        dispatchStrategy.start();

        if (logger.isTraceEnabled()) {
            logger.trace("{} successfully started for concurrency={}",
                dispatchStrategy.getClass().getSimpleName(), concurrency);
        }
    }

    /**
     * At least one dispatcher, never more than the machine's core count, and never so many that
     * any one of them would have fewer than {@link #MIN_PERMITS_PER_DISPATCHER} semaphore permits
     * to itself on average — see that constant's Javadoc for why.
     */
    static int dispatcherCountFor(int concurrency) {
        return Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), concurrency / MIN_PERMITS_PER_DISPATCHER));
    }

    /**
     * Runs the pipeline for {@code exchange} synchronously on the calling thread, bypassing this
     * service's own queue/{@link DispatchStrategy} entirely. Exposed (protected, not public) for
     * a subclass that manages its own intake and completion tracking itself — today only {@code
     * KafkaConsumerService} (see issue #64): it must know a record's pipeline execution has
     * actually finished, not merely been handed off to be run later, before it is safe to commit
     * that record's offset. A subclass using this should not also call {@code doStart()}/{@code
     * super.doStart()} — that would start a {@link DispatchStrategy} whose queue nothing ever
     * feeds, wasting a permanently-idle thread.
     */
    protected void dispatchToNext(Exchange exchange) {
        eventDrivenConsumer.dispatchToNext(exchange);
    }

    @Override
    public void doStop() {
        if (dispatchStrategy != null) {
            dispatchStrategy.stop(SHUTDOWN_TIMEOUT_MILLIS);
        }
        // Note: this only waits for THIS flow's own dispatch thread(s). Tasks already submitted to
        // the shared source worker pool are not awaited here — that pool is owned by
        // PipeliteContext, shared across flows, and outlives the stop of any single one; its own
        // shutdown is PipeliteContext#stop()'s responsibility.
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

    @Override
    public void setSourceWorkerPool(ExecutorService sourceWorkerPool) {
        this.sourceWorkerPool = sourceWorkerPool;
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
    public FlowNode getNext() {
        return eventDrivenConsumer.getNext();
    }

    @Override
    public String getProcessorName() {
        return eventDrivenConsumer.getProcessorName();
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
