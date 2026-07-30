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

import io.pipelite.spi.flow.exchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class EventDrivenConsumer extends DefaultConsumer {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    public static final Object POISON_PILL = new Object();

    private static final int DEFAULT_QUEUE_SIZE = 20;

    private static final String MDC_EXCHANGE_ID_KEY = "pipelite.exchangeId";

    protected final BlockingQueue<PriorityExchange> queue;

    private final AtomicLong exchangeCount;

    public EventDrivenConsumer(Endpoint endpoint) {
        this(endpoint, DEFAULT_QUEUE_SIZE);
    }

    public EventDrivenConsumer(Endpoint endpoint, int queueSize) {
        super(endpoint);
        queue = new PriorityBlockingQueue<>(queueSize);
        exchangeCount = new AtomicLong(0);
    }

    @Override
    public void process(Exchange exchange) {
        final long exchangeNumber = exchangeCount.incrementAndGet();
        try {
            preProcessExchange(exchange);
            queue.put(PriorityExchange.withNormalPriority(exchange, exchangeNumber));
            postProcessExchange(exchange);
            /*
            synchronized (this){
                if(tag != null && sysLogger.isTraceEnabled()){
                    sysLogger.trace("{} - Exchange #{} successfully enqueued, waiting...", tag, exchangeNumber);
                }
            }*/
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException exception){
            if(sysLogger.isErrorEnabled()){
                sysLogger.error("{} - An underlying error occurred enqueueing Exchange #{}", tag, exchangeNumber, exception);
            }
            throw exception;
        }
    }

    public int receive() {
        try {
            if (!hasNext()) {
                throw new IllegalStateException("DefaultConsumer does not have a next FlowNode");
            }
            final PriorityExchange priorityExchange = takeNext();
            final Exchange exchange = priorityExchange.getExchange();
            synchronized (this){
                if(tag != null && sysLogger.isTraceEnabled()){
                    sysLogger.trace("{} - Exchange #{} extracted from queue, processing.", tag, priorityExchange.priority);
                }
            }
            if(isPoisonPill(exchange)){
                if(tag != null && sysLogger.isTraceEnabled()){
                    sysLogger.trace("{} - Poison pill acquired, terminating.", tag);
                }
                return 0;
            }
            dispatchToNext(exchange);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return 1;
    }

    /**
     * Blocks until an {@link Exchange} is available. Exposed separately from {@link #receive()}
     * so a concurrent dispatch loop (see {@code EventDrivenConsumerService}, {@code concurrency > 1})
     * can pull from the queue on its own dedicated thread without also running {@link
     * #dispatchToNext} inline — the actual pipeline execution for that exchange is instead
     * submitted to the shared source worker pool.
     */
    PriorityExchange takeNext() throws InterruptedException {
        return queue.take();
    }

    boolean isPoisonPill(Exchange exchange) {
        return POISON_PILL.equals(exchange.getInputPayloadAs(Object.class));
    }

    /**
     * Dispatches {@code exchange} to {@code next}, i.e. runs the pipeline attached to this
     * consumer synchronously to completion. Named distinctly from {@link #process(Exchange)}
     * (overridden in this class to mean "enqueue") so callers outside this class — which can't
     * do {@code super.process(...)} — have an unambiguous way to invoke the same behavior
     * {@link #receive()} already gets via its own {@code super.process(exchange)} call.
     * Sets the exchange's correlation id in the SLF4J MDC for the duration of the call, so
     * concurrent workers produce attributable, non-interleaved log lines.
     */
    void dispatchToNext(Exchange exchange) {
        final String previousCorrelationId = MDC.get(MDC_EXCHANGE_ID_KEY);
        MDC.put(MDC_EXCHANGE_ID_KEY, exchange.getInput().getId());
        try {
            super.process(exchange);
        } finally {
            if (previousCorrelationId != null) {
                MDC.put(MDC_EXCHANGE_ID_KEY, previousCorrelationId);
            } else {
                MDC.remove(MDC_EXCHANGE_ID_KEY);
            }
        }
    }

    public static class PriorityExchange implements Comparable<PriorityExchange> {

        private final Exchange exchange;
        private final long priority;

        public static PriorityExchange withMaxPriority(Exchange exchange) {
            return new PriorityExchange(exchange, Integer.MIN_VALUE);
        }

        public static PriorityExchange withNormalPriority(Exchange exchange, long ticketNumber) {
            return new PriorityExchange(exchange, ticketNumber);
        }

        private PriorityExchange(Exchange exchange, long priority) {
            this.exchange = exchange;
            this.priority = priority;
        }

        public Exchange getExchange() {
            return exchange;
        }

        @Override
        public int compareTo(PriorityExchange other) {
            return Long.compare(priority, other.priority);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PriorityExchange that = (PriorityExchange) o;
            return Objects.equals(exchange, that.exchange);
        }

        @Override
        public int hashCode() {
            return Objects.hash(exchange);
        }
    }
}
