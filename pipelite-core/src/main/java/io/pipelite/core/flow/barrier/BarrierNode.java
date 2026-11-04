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
package io.pipelite.core.flow.barrier;

import io.pipelite.common.support.serialization.Base64ObjectSerializer;
import io.pipelite.common.support.serialization.ByteArrayWriteException;
import io.pipelite.common.support.serialization.ObjectSerializer;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.context.internal.IOKeys;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.definition.builder.barrier.BarrierSpec;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.exchange.internal.HeadersImpl;
import io.pipelite.core.flow.internal.AbstractFlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Makes the exchange that reaches it wait until the flows it awaits have completed, and then lets it go on
 * unchanged (issue #150).
 * <p>
 * It does not block anything. The exchange is kept whole in the {@link BarrierRepository}, and this node returns:
 * the worker is free, and the arrival is acknowledged to the inbox of its flow, which is safe because the wait is
 * written before. When the last flow completes, the {@link BarrierCoordinator} resumes the exchange on the node
 * after this one, on the worker pool of the flow it belongs to.
 * <p>
 * Package-private (#82): construct via {@link BarrierNodeFactory#barrier(BarrierSpec)}.
 */
class BarrierNode extends AbstractFlowNode implements PipeliteContextAware {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final BarrierSpec spec;
    private final Supplier<Instant> clock;
    private final ObjectSerializer serializer = new Base64ObjectSerializer();
    private ExchangeFactory exchangeFactory;
    private BarrierRepository repository;

    BarrierNode(BarrierSpec spec) {
        this(spec, Instant::now);
    }

    BarrierNode(BarrierSpec spec, Supplier<Instant> clock) {
        this.spec = Objects.requireNonNull(spec, "spec is required and cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock is required and cannot be null");
    }

    @Override
    public void setPipeliteContext(InternalPipeliteContext pipeliteContext) {
        Objects.requireNonNull(pipeliteContext, "pipeliteContext is required and cannot be null");
        this.exchangeFactory = pipeliteContext.getExchangeFactory();
        this.repository = pipeliteContext.getBarrierRepository();
        // known here: the flow and the node have been named before the dependencies are injected
        pipeliteContext.getBarrierCoordinator().register(barrierId(), spec);
    }

    BarrierSpec spec() {
        return spec;
    }

    /** Stable and unique in a context: {@code <flow>/<node>}. */
    String barrierId() {
        return getFlowName() + "/" + getProcessorName();
    }

    @Override
    public void process(ExchangeImpl exchange) {

        final WaitDisposition disposition;
        try {
            preProcessExchange(exchange);
            disposition = repository.arrive(waiterOf(exchange));
        } catch (RuntimeException exception) {
            sysLogger.error("An underlying error occurred processing message", exception);
            handleFailure(exception, exchange);
            return;
        }

        switch (disposition.kind()) {
            case HOLD -> {
                ExecutionTracker.markWaiting(exchange);
                sysLogger.debug("Barrier '{}': the exchange waits for the flows it awaits", barrierId());
            }
            case DISCARD -> {
                ExecutionTracker.markWaiting(exchange);
                sysLogger.debug("Barrier '{}': the exchange was already released or failed, left to the recovery", barrierId());
            }
            case PROCEED -> proceed(exchange);
            case FAIL -> fail(exchange, disposition.failedBy());
        }
    }

    /** What the flow does once the wait is over: the exchange goes on to the next node, unchanged. */
    void proceed(ExchangeImpl exchange) {
        postProcessExchange(exchange);
        if (next != null) {
            next.process(exchangeFactory.nextExchange(exchange));
        }
    }

    /** What the flow does when a flow it waited for failed: the error handling of the flow. */
    void fail(ExchangeImpl exchange, Pending failedBy) {
        handleFailure(new BarrierFailedException(barrierId(), failedBy), exchange);
    }

    private Waiter waiterOf(ExchangeImpl exchange) {

        final Set<Pending> pending = new LinkedHashSet<>();
        for (BarrierSpec.Await await : spec.getAwaits()) {
            pending.add(new Pending(await.queueName(), CorrelationKeys.keyOf(await.correlation(), exchange)));
        }

        // What is kept is the same exchange, with the same messages (so the same id: it is one exchange before and
        // after the wait, in the logs and in the dumps), but without the inbox entry it holds: that entry is
        // acknowledged when this node returns, and must not follow the exchange when it resumes. The live
        // exchange is not touched.
        final Map<String, Object> properties = new ConcurrentHashMap<>();
        exchange.propertySet().forEach(property -> properties.put(property.getKey(), property.getValue()));
        properties.remove(IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME);
        properties.remove(IOKeys.DURABLE_INBOX_RESUPPLIED_ENTRY_ID_PROPERTY_NAME);
        final ExchangeImpl kept = new ExchangeImpl(exchange.getInput(), exchange.getOutput(),
            new HeadersImpl((HeadersImpl) exchange.getHeaders()), properties);

        final String exchangeData;
        try {
            exchangeData = serializer.serializeObject(kept);
        } catch (ByteArrayWriteException exception) {
            throw new IllegalStateException(String.format(
                "The exchange cannot wait at barrier '%s': it cannot be serialized, so its payload and every header must be serializable",
                barrierId()), exception);
        }

        final Instant now = clock.get();
        return Waiter.waiting(barrierId() + "#" + exchange.getInput().getId(), barrierId(), getFlowName(),
            getProcessorName(), exchangeData, serializer.getEncoding(), pending, now, now.plus(spec.getTimeout()));
    }
}
