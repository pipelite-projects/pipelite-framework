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
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.inbox.DurableInbox;
import io.pipelite.spi.inbox.NoOpDurableInbox;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

public class DefaultPollingConsumer extends AbstractConsumer implements PollingConsumer, DurableInboxAware {

    /**
     * Independent of the queue's own JDK capacity (which defaults to {@code Integer.MAX_VALUE} —
     * effectively unbounded, see the default constructor below): the gate is what actually applies
     * backpressure, not the queue itself. See {@link QueuePressureGate}.
     */
    private static final int DEFAULT_PRESSURE_HIGH_WATERMARK = 20;

    private static final ObjectToByteArrayConverter EXCHANGE_TO_BYTES = new ObjectToByteArrayConverter();

    // Debug-only (issue #70): identify a pending inbox entry without deserializing its
    // Java-serialized payload - exchangeId matches EventDrivenConsumer's own MDC correlation id,
    // so it can be grepped straight out of application logs.
    private static final String METADATA_EXCHANGE_ID_KEY = "exchangeId";

    private static final String METADATA_FLOW_NAME_KEY = "flowName";

    private final Object LOCK = new Object();

    protected final BlockingQueue<Exchange> queue;

    private final QueuePressureGate pressureGate;

    /**
     * Defaults to a no-op so {@link #consume}/{@link #process} can call it unconditionally (see
     * {@link NoOpDurableInbox}) — real wiring happens via {@link #setDurableInbox} at flow-build
     * time, only for sources that didn't opt out (issue #70).
     */
    private DurableInbox durableInbox = NoOpDurableInbox.INSTANCE;

    public DefaultPollingConsumer(Endpoint endpoint) {
        this(endpoint, Integer.MAX_VALUE);
    }

    public DefaultPollingConsumer(Endpoint endpoint, int queueSize) {
        super(endpoint);
        queue = new LinkedBlockingDeque<>(queueSize);
        pressureGate = QueuePressureGate.withDefaultHysteresis(DEFAULT_PRESSURE_HIGH_WATERMARK);
    }

    @Override
    public Exchange receive() {
        synchronized (LOCK){
            final Exchange exchange = queue.poll();
            if (exchange != null) {
                pressureGate.afterDequeue(queue::size);
            }
            return exchange;
        }
    }

    @Override
    public Exchange receive(long timeout) {
        synchronized (LOCK) {
            try {
                final Exchange exchange = queue.poll(timeout, TimeUnit.MILLISECONDS);
                if (exchange != null) {
                    pressureGate.afterDequeue(queue::size);
                }
                return exchange;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    @Override
    public Exchange receiveNoWait() {
        return receive(0);
    }

    @Override
    public void setDurableInbox(DurableInbox durableInbox) {
        this.durableInbox = durableInbox;
    }

    @Override
    public void consume(Exchange exchange) {
        // Deliberately outside the synchronized(LOCK) block below: blocking here while holding
        // LOCK would deadlock against receive()/receive(timeout), which need that same lock to
        // dequeue and release pressure via afterDequeue(...).
        try {
            pressureGate.beforeEnqueue(queue::size);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }
        // Durable write-through (issue #70): must complete before queue.put, same reasoning as
        // EventDrivenConsumer#process - a no-op when durability is disabled for this source.
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
        synchronized (LOCK){
            try{
                queue.put(exchange);
            }catch(InterruptedException exception){
                Thread.currentThread().interrupt();
            }
        }
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

    @Override
    public void process(Exchange exchange) {
        if(next != null){
            // Captured BEFORE dispatch, not read back off `exchange` after (issue #70) - same
            // reasoning as EventDrivenConsumer#dispatchToNext: a downstream node can hand this
            // exact Exchange instance to a DIFFERENT flow's consumer, whose own write-through
            // hook would otherwise overwrite this property with its own entry id first.
            final String entryId = exchange.getProperty(IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME, String.class);
            next.process(exchange);
            // Terminal state: next.process(...) returning at all means either success or a caught
            // exception was already routed to a retry channel; only a genuinely unhandled
            // exception skips this.
            if (entryId != null) {
                durableInbox.acknowledge(entryId);
            }
        }
    }
}
