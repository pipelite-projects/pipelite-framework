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
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

public class EventDrivenConsumerService extends AbstractService implements Consumer, ExchangeFactoryAware, SourceWorkerPoolAware {

    private static final String WORKER_PREFIX = "EDC";
    private static final long SHUTDOWN_TIMEOUT_MILLIS = 30_000L;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final class EventDrivenConsumerReceiveTask implements Runnable {

        @Override
        public void run() {
            if(!isRunAllowed()){
                throw new IllegalStateException("Service run not allowed, status is " + EventDrivenConsumerService.this.getStatus());
            }
            while (isRunAllowed()) {
                if(eventDrivenConsumer.receive() < 1){
                    break;
                }
            }
        }
    }

    private final class EventDrivenConsumerPoisonPillTask implements Runnable {

        @Override
        public void run() {
            final Exchange poisonPill = exchangeFactory.createExchange(EventDrivenConsumer.POISON_PILL);
            eventDrivenConsumer.consume(poisonPill);
        }
    }

    /**
     * Used only when {@code concurrency > 1}: a single dedicated thread (same role as {@link
     * EventDrivenConsumerReceiveTask}, never more than one per flow) that just drains the queue
     * and hands each Exchange off to the shared source worker pool, gated by {@link
     * #dispatchSemaphore} — the actual pipeline execution happens on a pool thread, not here.
     * {@code dispatchSemaphore.acquire()} blocking (permits exhausted) is exactly what stops this
     * thread from taking further Exchanges off the queue, which is the backpressure mechanism:
     * it has nothing to do with the queue's own bounded capacity, and needs none.
     */
    private final class ConcurrentDispatchTask implements Runnable {

        @Override
        public void run() {
            if(!isRunAllowed()){
                throw new IllegalStateException("Service run not allowed, status is " + EventDrivenConsumerService.this.getStatus());
            }
            while (isRunAllowed()) {
                try {
                    final EventDrivenConsumer.PriorityExchange priorityExchange = eventDrivenConsumer.takeNext();
                    final Exchange exchange = priorityExchange.getExchange();
                    if (eventDrivenConsumer.isPoisonPill(exchange)) {
                        break;
                    }
                    dispatchSemaphore.acquire();
                    sourceWorkerPool.submit(() -> {
                        try {
                            eventDrivenConsumer.dispatchToNext(exchange);
                        } catch (Throwable t) {
                            if (logger.isErrorEnabled()) {
                                logger.error("Unhandled error dispatching an Exchange on the shared source worker pool", t);
                            }
                        } finally {
                            dispatchSemaphore.release();
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private final EventDrivenConsumer eventDrivenConsumer;
    protected final ThreadFactory threadFactory;
    protected ExchangeFactory exchangeFactory;
    protected ExecutorService sourceWorkerPool;

    private Thread receiveTask;
    private Thread poisonPillTask;
    private Thread dispatchThread;
    private Semaphore dispatchSemaphore;

    public EventDrivenConsumerService(EventDrivenConsumer eventDrivenConsumer) {
        this.eventDrivenConsumer = eventDrivenConsumer;
        threadFactory = new DefaultThreadFactory(WORKER_PREFIX);
    }

    @Override
    public void doStart() {

        final EndpointProperties properties = eventDrivenConsumer.getEndpoint().getProperties();
        final int concurrency = properties.getAsIntegerOrDefault(SourceConcurrencyProperties.CONCURRENCY, 1);
        // Validated even though BOUNDED_POOL is the only value today (mirrors TimeUnit.valueOf
        // in ScheduledPollingConsumerService) — an unrecognized value fails fast at startup.
        ExecutorType.valueOf(properties.getOrDefault(SourceConcurrencyProperties.EXECUTOR_TYPE, ExecutorType.BOUNDED_POOL.name()));

        if (concurrency <= 1) {
            // Unchanged from before this feature existed: one dedicated thread, inline processing.
            if (receiveTask == null) {
                receiveTask = threadFactory.newThread(new EventDrivenConsumerReceiveTask());
            }
            receiveTask.start();

            if(logger.isTraceEnabled()){
                logger.trace("EventDrivenConsumerReceiveTask successfully started");
            }
            return;
        }

        dispatchSemaphore = new Semaphore(concurrency);
        if (dispatchThread == null) {
            dispatchThread = threadFactory.newThread(new ConcurrentDispatchTask());
        }
        dispatchThread.start();

        if(logger.isTraceEnabled()){
            logger.trace("ConcurrentDispatchTask successfully started with concurrency={}", concurrency);
        }
    }

    @Override
    public void doStop() {

        if (poisonPillTask == null) {
            poisonPillTask = threadFactory.newThread(new EventDrivenConsumerPoisonPillTask());
        }
        poisonPillTask.start();

        if(logger.isTraceEnabled()){
            logger.trace("EventDrivenWorker successfully started");
        }

        // Wait for the active worker thread (whichever mode was started) to actually terminate,
        // instead of returning immediately once the poison pill has merely been sent.
        final Thread workerThread = dispatchThread != null ? dispatchThread : receiveTask;
        if (workerThread != null) {
            try {
                workerThread.join(SHUTDOWN_TIMEOUT_MILLIS);
                if (workerThread.isAlive() && logger.isWarnEnabled()) {
                    logger.warn("Worker thread {} did not terminate within {}ms during stop()",
                        workerThread.getName(), SHUTDOWN_TIMEOUT_MILLIS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Note: this only waits for THIS flow's own dispatch thread. Tasks already submitted to
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
