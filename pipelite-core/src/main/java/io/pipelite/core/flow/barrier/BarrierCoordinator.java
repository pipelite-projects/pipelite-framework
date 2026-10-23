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

import io.pipelite.common.support.serialization.BaseEncoding;
import io.pipelite.common.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.core.consumer.internal.EventDrivenConsumerService;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.definition.builder.barrier.BarrierSpec;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.flow.FlowNodeLocator;
import io.pipelite.core.flow.internal.AbstractFlowNode;
import io.pipelite.core.flow.internal.Flow;
import io.pipelite.core.flow.internal.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * What makes a waiting exchange go on, once the flows it awaits have completed (issue #150).
 * <p>
 * The barrier nodes tell it which flows are awaited and for how long. The rest of the framework tells it when a
 * flow completed or failed for a key ({@link #flowCompleted}, {@link #flowFailed}); the repository says which
 * waiters that releases, and each one is resumed <strong>on the worker pool of the flow it belongs to</strong>, under
 * that flow's {@code concurrency}, never on the thread of the flow that completed: what goes wrong further down the
 * waiting flow must not reach the flow that completed. A flow that is not read by an event-driven consumer (a
 * polling source has no dispatch of its own) is resumed on the shared worker pool.
 * <p>
 * The wait is removed from the repository only when the resume has finished, so a crash in between leaves it
 * resolved, and {@link #recover()} resumes it when the context starts.
 */
public final class BarrierCoordinator {

    /** Runs {@code target} with {@code exchange} on the workers of {@code flow}; {@code onComplete} when it has finished. */
    interface Dispatcher {
        void dispatch(Flow flow, FlowNode target, ExchangeImpl exchange, Runnable onComplete);
    }

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final InternalPipeliteContext context;
    private final Supplier<Instant> clock;
    private final Dispatcher dispatcher;
    private final ByteArrayToObjectConverter deserializer = new ByteArrayToObjectConverter();

    /** The barriers of the context, by id: what they await and for how long. */
    private final Map<String, BarrierSpec> barriers = new ConcurrentHashMap<>();
    /** The waiters being resumed now, so a recovery that runs meanwhile does not resume them a second time. */
    private final Set<String> resuming = ConcurrentHashMap.newKeySet();

    public BarrierCoordinator(InternalPipeliteContext context) {
        this(context, Instant::now, null);
    }

    BarrierCoordinator(InternalPipeliteContext context, Supplier<Instant> clock, Dispatcher dispatcher) {
        this.context = java.util.Objects.requireNonNull(context, "context is required and cannot be null");
        this.clock = java.util.Objects.requireNonNull(clock, "clock is required and cannot be null");
        this.dispatcher = dispatcher != null ? dispatcher : this::dispatchOnFlow;
    }

    void register(String barrierId, BarrierSpec spec) {
        barriers.put(barrierId, spec);
    }

    /** Whether some barrier awaits the flow that reads the queue {@code queueName}. */
    public boolean isAwaited(String queueName) {
        return barriers.values().stream()
            .anyMatch(spec -> spec.getAwaits().stream().anyMatch(await -> await.queueName().equals(queueName)));
    }

    /** How long a completion of the flow that reads {@code queueName} has to be remembered: the longest wait of a barrier that awaits it. */
    public Duration retention(String queueName) {
        return barriers.values().stream()
            .filter(spec -> spec.getAwaits().stream().anyMatch(await -> await.queueName().equals(queueName)))
            .map(BarrierSpec::getTimeout)
            .max(Duration::compareTo)
            .orElse(Duration.ZERO);
    }

    /** The flow that reads {@code queueName} completed its execution for {@code key}. */
    public void flowCompleted(String queueName, String key) {
        repository().complete(queueName, key, clock.get().plus(retention(queueName))).forEach(this::resume);
    }

    /** The flow that reads {@code queueName} failed definitively for {@code key}. */
    public void flowFailed(String queueName, String key) {
        repository().fail(queueName, key, clock.get().plus(retention(queueName))).forEach(this::resume);
    }

    /** Resumes what a crash left resolved but not resumed. To run once the flows have started. */
    public void recover() {
        for (Waiter waiter : repository().findResolved()) {
            resume(waiter);
        }
    }

    private BarrierRepository repository() {
        return context.getBarrierRepository();
    }

    void resume(Waiter waiter) {
        if (!resuming.add(waiter.id())) {
            return;
        }
        try {
            final Optional<Flow> flow = context.tryFindFlowByName(waiter.flowName());
            final FlowNode found = flow.flatMap(f -> FlowNodeLocator.findByProcessorName(f, waiter.nodeName())).orElse(null);
            if (!(found instanceof BarrierNode node)) {
                // The flow was renamed or removed since the wait was written: it stays, for a context that has it again.
                sysLogger.warn("Unable to resume the wait {}: no registered flow '{}' has the barrier '{}'",
                    waiter.id(), waiter.flowName(), waiter.nodeName());
                resuming.remove(waiter.id());
                return;
            }
            final ExchangeImpl exchange = deserialize(waiter);
            final Consumer<ExchangeImpl> action = waiter.status() == Waiter.Status.FAILED
                ? kept -> node.fail(kept, waiter.failedBy())
                : node::proceed;
            final ResumeNode target = new ResumeNode(action);
            target.setFlowName(waiter.flowName());
            target.setProcessorName(waiter.nodeName());
            dispatcher.dispatch(flow.get(), target, exchange, () -> finished(waiter));
        } catch (RuntimeException exception) {
            sysLogger.error("Unable to resume the wait {}: it stays resolved, for the recovery", waiter.id(), exception);
            resuming.remove(waiter.id());
        }
    }

    private void finished(Waiter waiter) {
        try {
            repository().remove(waiter.id());
        } finally {
            resuming.remove(waiter.id());
        }
    }

    private ExchangeImpl deserialize(Waiter waiter) {
        return deserializer.convert(BaseEncoding.base64().decode(waiter.exchangeData()), ExchangeImpl.class);
    }

    private void dispatchOnFlow(Flow flow, FlowNode target, ExchangeImpl exchange, Runnable onComplete) {
        if (flow.isConsumerOfType(EventDrivenConsumerService.class)) {
            flow.getConsumerAs(EventDrivenConsumerService.class).resumeAt(target, exchange, onComplete);
        } else {
            context.getSourceWorkerPool().execute(() -> {
                try {
                    target.process(exchange);
                } finally {
                    onComplete.run();
                }
            });
        }
    }

    /** A node that runs one step of the resume: it is what the workers of the flow are asked to process. */
    private static final class ResumeNode extends AbstractFlowNode {

        private final Consumer<ExchangeImpl> action;

        private ResumeNode(Consumer<ExchangeImpl> action) {
            this.action = action;
        }

        @Override
        public void process(ExchangeImpl exchange) {
            action.accept(exchange);
        }
    }
}
