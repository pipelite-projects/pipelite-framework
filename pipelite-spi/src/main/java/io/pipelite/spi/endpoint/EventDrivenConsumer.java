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

import io.pipelite.common.support.serialization.ObjectToByteArrayConverter;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.concurrent.QueuePressureGate;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.inbox.DurableInbox;
import io.pipelite.spi.inbox.NoOpDurableInbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class EventDrivenConsumer extends DefaultConsumer implements DurableInboxAware {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    public static final Object POISON_PILL = new Object();

    private static final int DEFAULT_QUEUE_SIZE = 20;

    private static final String MDC_EXCHANGE_ID_KEY = "pipelite.exchangeId";

    private static final String MDC_FLOW_NAME_KEY = "pipelite.flowName";

    // Debug-only (issue #70): identify a pending inbox entry without deserializing its
    // Java-serialized payload - exchangeId is the same value already written to
    // MDC_EXCHANGE_ID_KEY above, so it can be grepped straight out of application logs.
    private static final String METADATA_EXCHANGE_ID_KEY = "exchangeId";

    private static final String METADATA_FLOW_NAME_KEY = "flowName";

    private static final ObjectToByteArrayConverter EXCHANGE_TO_BYTES = new ObjectToByteArrayConverter();

    protected final BlockingQueue<PriorityExchange> queue;

    private final AtomicLong exchangeCount;

    /**
     * Defaults to a no-op so every write-through/acknowledge call site below can stay
     * unconditional (see {@link NoOpDurableInbox}) — real wiring happens via {@link
     * #setDurableInbox} at flow-build time, only for sources that didn't opt out (issue #70).
     */
    private DurableInbox durableInbox = NoOpDurableInbox.INSTANCE;

    /**
     * See {@link QueuePressureGate}: the queue above is unbounded in practice (confirmed
     * empirically, see its own comment), so nothing short of this gate ever signals "slow down"
     * back to whoever is calling {@link #process(ExchangeImpl)} — an HTTP handler thread, a {@code
     * link://}-producing flow, etc. Sized off {@code queueSize}, which until now only affected
     * {@code PriorityBlockingQueue}'s initial array size and had no other effect.
     */
    private final QueuePressureGate pressureGate;

    public EventDrivenConsumer(Endpoint endpoint) {
        this(endpoint, DEFAULT_QUEUE_SIZE);
    }

    public EventDrivenConsumer(Endpoint endpoint, int queueSize) {
        super(endpoint);
        // SPIKE (issue #49 tier-2): PriorityBlockingQueue swapped for LinkedBlockingQueue.
        // PriorityExchange.withMaxPriority() is unused anywhere in the codebase (confirmed by
        // repo-wide search) - every message, poison pills included, goes through
        // withNormalPriority() with a strictly increasing ticket number, so the queue already
        // behaves as plain FIFO in practice. PriorityBlockingQueue guards both put() and take()
        // with a single shared lock (needed to maintain its heap invariant), which becomes
        // severely contended with multiple concurrent consumer threads (see MultiConsumerTask);
        // LinkedBlockingQueue's separate put/take locks don't have this problem. The queue itself
        // is left unbounded here deliberately (see QueuePressureGate above for why): a hard JDK
        // queue capacity would throw/reject on overflow instead of applying backpressure.
        queue = new LinkedBlockingQueue<>();
        exchangeCount = new AtomicLong(0);
        pressureGate = QueuePressureGate.withDefaultHysteresis(queueSize);
    }

    @Override
    public void setDurableInbox(DurableInbox durableInbox) {
        this.durableInbox = durableInbox;
    }

    @Override
    public void process(ExchangeImpl exchange) {
        final long exchangeNumber = exchangeCount.incrementAndGet();
        try {
            preProcessExchange(exchange);
            // postProcessExchange (marks this consumer as the last-executed processor) must run
            // BEFORE queue.put, not after: put/take on a BlockingQueue is the only happens-before
            // edge to the dispatch thread that later dequeues and runs this Exchange. Setting the
            // property after put() races that thread — under the wrong scheduling (observed on CI,
            // rarely locally) the dispatch thread can dequeue, run the next FlowNode, and have it
            // throw before this thread gets to postProcessExchange, so a downstream ExceptionHandler
            // reading FLOW_EXECUTION_LAST_EXECUTED_PROCESSOR_PROPERTY_NAME sees it still unset.
            postProcessExchange(exchange);
            // The poison pill bypasses backpressure deliberately: shutdown should never be delayed
            // by load, and something is by definition still draining the queue (the very threads
            // this pill is meant to stop), so pressure would eventually release on its own anyway —
            // there's no reason for doStop() to wait for that.
            if (!isPoisonPill(exchange)) {
                pressureGate.beforeEnqueue(queue::size);
                // Durable write-through (issue #70), same happens-before reasoning as
                // postProcessExchange above: must complete before queue.put so the dispatch thread
                // that dequeues this Exchange already sees the entry-id property. A no-op when
                // durability is disabled for this source (NoOpDurableInbox).
                final String resupliedEntryId = exchange.getProperty(IOKeys.DURABLE_INBOX_RESUPPLIED_ENTRY_ID_PROPERTY_NAME, String.class);
                if (resupliedEntryId != null) {
                    // A recovery resupply (see IOKeys' own Javadoc on this property) - resume the
                    // ORIGINAL entry instead of writing a new, duplicate one.
                    exchange.setProperty(IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME, resupliedEntryId);
                    exchange.removeProperty(IOKeys.DURABLE_INBOX_RESUPPLIED_ENTRY_ID_PROPERTY_NAME);
                } else {
                    final String entryId = durableInbox.enqueue(EXCHANGE_TO_BYTES.convert(exchange),
                        inboxMetadata(exchange.getInput().getId(), getFlowName()));
                    if (entryId != null) {
                        exchange.setProperty(IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME, entryId);
                    }
                }
            }
            queue.put(PriorityExchange.withNormalPriority(exchange, exchangeNumber));
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
            final ExchangeImpl exchange = priorityExchange.getExchange();
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
     * Blocks until an {@link ExchangeImpl} is available. Exposed separately from {@link #receive()}
     * so a concurrent dispatch loop (see {@code EventDrivenConsumerService}, {@code concurrency > 1})
     * can pull from the queue on its own dedicated thread without also running {@link
     * #dispatchToNext} inline — the actual pipeline execution for that exchange is instead
     * submitted to the shared source worker pool.
     */
    PriorityExchange takeNext() throws InterruptedException {
        final PriorityExchange result = queue.take();
        pressureGate.afterDequeue(queue::size);
        return result;
    }

    boolean isPoisonPill(ExchangeImpl exchange) {
        return POISON_PILL.equals(exchange.getInputPayloadAs(Object.class));
    }

    /**
     * {@code flowName} is {@code null} until {@link #setFlowName} is called (e.g. a consumer
     * built directly in a test rather than via {@code FlowFactory}) - {@code Map.of} would throw
     * on a null value, so entries are only included when actually available instead of forcing a
     * placeholder into a debug-only field.
     */
    private static Map<String, String> inboxMetadata(String exchangeId, String flowName) {
        final Map<String, String> metadata = new java.util.LinkedHashMap<>();
        if (exchangeId != null) {
            metadata.put(METADATA_EXCHANGE_ID_KEY, exchangeId);
        }
        if (flowName != null) {
            metadata.put(METADATA_FLOW_NAME_KEY, flowName);
        }
        return metadata;
    }

    /**
     * Dispatches {@code exchange} to {@code next}, i.e. runs the pipeline attached to this
     * consumer synchronously to completion. Named distinctly from {@link #process(ExchangeImpl)}
     * (overridden in this class to mean "enqueue") so callers outside this class — which can't
     * do {@code super.process(...)} — have an unambiguous way to invoke the same behavior
     * {@link #receive()} already gets via its own {@code super.process(exchange)} call.
     * Sets the exchange's correlation id and this consumer's flow name in the SLF4J MDC for the
     * duration of the call, so concurrent workers produce attributable, non-interleaved log
     * lines even though — unlike at {@code concurrency=1}, where the dedicated thread's own name
     * already carries the flow identity — a shared source worker pool thread's name no longer
     * does (see {@code EventDrivenConsumerService.ConcurrentDispatchTask}, which relies on this
     * MDC value instead of renaming the thread per message).
     */
    void dispatchToNext(ExchangeImpl exchange) {
        final String previousCorrelationId = MDC.get(MDC_EXCHANGE_ID_KEY);
        final String previousFlowName = MDC.get(MDC_FLOW_NAME_KEY);
        MDC.put(MDC_EXCHANGE_ID_KEY, exchange.getInput().getId());
        MDC.put(MDC_FLOW_NAME_KEY, getFlowName());
        // Captured BEFORE dispatch, not read back off `exchange` after (issue #70): a node
        // further down this flow's own chain (a plain, uncopied `.toSink("link://...")` producer,
        // unlike e.g. WireTapProcessorNode, which deliberately taps a copy) can hand this exact
        // Exchange instance straight to a DIFFERENT flow's consumer, whose own write-through hook
        // overwrites this same property with ITS OWN entry id before control ever returns here -
        // verified as a real, reproduced bug (PipeliteFlowLinkIntegrationTest's origin-flow entry
        // silently never acknowledged), not a theoretical one.
        final String entryId = exchange.getProperty(IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME, String.class);
        try {
            super.process(exchange);
            // Terminal state (issue #70): super.process(...) returning at all - whether the
            // pipeline actually succeeded, or a RuntimeException was caught and routed to a retry
            // channel by AbstractProcessorNode's own exceptionHandler branch - means nothing more
            // will ever act on this Exchange from here. Only a genuinely unhandled exception
            // escaping all the way up here skips this line, leaving the entry pending for
            // redelivery on restart - the correct at-least-once outcome.
            if (entryId != null) {
                durableInbox.acknowledge(entryId);
            }
        } finally {
            if (previousCorrelationId != null) {
                MDC.put(MDC_EXCHANGE_ID_KEY, previousCorrelationId);
            } else {
                MDC.remove(MDC_EXCHANGE_ID_KEY);
            }
            if (previousFlowName != null) {
                MDC.put(MDC_FLOW_NAME_KEY, previousFlowName);
            } else {
                MDC.remove(MDC_FLOW_NAME_KEY);
            }
        }
    }

    public static class PriorityExchange implements Comparable<PriorityExchange> {

        private final ExchangeImpl exchange;
        private final long priority;

        public static PriorityExchange withMaxPriority(ExchangeImpl exchange) {
            return new PriorityExchange(exchange, Integer.MIN_VALUE);
        }

        public static PriorityExchange withNormalPriority(ExchangeImpl exchange, long ticketNumber) {
            return new PriorityExchange(exchange, ticketNumber);
        }

        private PriorityExchange(ExchangeImpl exchange, long priority) {
            this.exchange = exchange;
            this.priority = priority;
        }

        public ExchangeImpl getExchange() {
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
