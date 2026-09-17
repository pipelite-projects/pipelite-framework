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
import io.pipelite.spi.inbox.DurableInbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduledPollingConsumerService extends AbstractService implements PollingConsumer, DurableInboxAware {

    /**
     * Default of 1 reproduces today's exact behavior for every existing consumer built on this
     * class, several of which depend on it: {@code TimePollingConsumer#receive()} never returns
     * null (it fires unconditionally on every call), so a consumer that opts into a batch size
     * greater than 1 must genuinely be able to signal "nothing more right now" via a null return
     * — draining until null is only safe for a {@link PollingConsumer} with that contract (e.g.
     * {@code RetryPollingConsumer}, backed by a repository that legitimately empties out).
     */
    private static final int DEFAULT_BATCH_SIZE = 1;

    /**
     * Generous but finite, same philosophy as {@code FlowExecutionDumpInMemoryRepository
     * .DEFAULT_MAX_SIZE} and {@code RetryChannelDefinitionFactory.RETRY_BATCH_SIZE} (50, the only
     * production caller of this property today, well under this cap). {@code batchSize} is a
     * plain endpoint-URL query parameter — nothing stops a caller from configuring an arbitrarily
     * large value, which would otherwise make a single scheduled tick drain that many items
     * sequentially before yielding back to the scheduler, on a single thread shared with every
     * other consumer scheduled on the same {@code consumerPool}. Rejecting an unreasonable value
     * at {@code doStart()} fails fast instead of silently degrading the whole scheduler.
     */
    static final int MAX_BATCH_SIZE = 1000;

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

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

        final Long period = endpointProperties.getAsLongOrDefault(PollingProperties.PERIOD, 1000L);
        final Long initialDelay = endpointProperties.getAsLongOrDefault(PollingProperties.INITIAL_DELAY, 0L);

        final String timeUnitAsText = endpointProperties.getOrDefault(PollingProperties.TIME_UNIT, TimeUnit.MILLISECONDS.name());
        final TimeUnit timeUnit = TimeUnit.valueOf(timeUnitAsText);

        final int batchSize = endpointProperties.getAsIntegerOrDefault(PollingProperties.BATCH_SIZE, DEFAULT_BATCH_SIZE);
        if (batchSize < 1 || batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(String.format(
                "%s must be between 1 and %d, got %d", PollingProperties.BATCH_SIZE, MAX_BATCH_SIZE, batchSize));
        }

        ScheduledFuture<?> consumer = consumerPool.scheduleAtFixedRate(() -> {
            try {
                // Bounded, not "drain until empty": a backlog larger than batchSize is simply
                // finished across further ticks, rather than one tick monopolizing this consumer's
                // single-threaded executor for however long an unbounded drain would take.
                for (int i = 0; i < batchSize; i++) {
                    final Exchange exchange = pollingConsumer.receive();
                    if (exchange == null) {
                        break;
                    }
                    pollingConsumer.process(exchange);
                }
            } catch (Throwable t) {
                // ScheduledExecutorService#scheduleAtFixedRate silently cancels every future
                // execution of this task the first time it throws - with no log, no exception
                // anywhere, the periodic poll just stops forever. Mirrors the same resilience
                // discipline already applied to EventDrivenConsumerService's dispatch strategies
                // (#49/#50) and KafkaConsumerTask's poll loop (#64): one bad poll/process must
                // not permanently kill this consumer.
                if (sysLogger.isErrorEnabled()) {
                    sysLogger.error("Unhandled error in the scheduled poll loop", t);
                }
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
    public void setNext(FlowNode next) {
        pollingConsumer.setNext(next);
    }

    @Override
    public boolean hasNext() {
        return pollingConsumer.hasNext();
    }

    @Override
    public FlowNode getNext() {
        return pollingConsumer.getNext();
    }

    @Override
    public String getProcessorName() {
        return pollingConsumer.getProcessorName();
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

    /**
     * {@code pollingConsumer} is typed as the general {@link PollingConsumer} interface, not
     * every implementation durably records its intake (issue #70) — {@code instanceof}-guarded
     * the same way {@code FlowNodeConfigurer.injectDependencies} already treats other optional
     * {@code *Aware} capabilities, rather than widening {@link PollingConsumer} itself.
     */
    @Override
    public void setDurableInbox(DurableInbox durableInbox) {
        if (pollingConsumer instanceof DurableInboxAware) {
            ((DurableInboxAware) pollingConsumer).setDurableInbox(durableInbox);
        }
    }
}
