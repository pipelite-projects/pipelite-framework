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
import io.pipelite.core.context.internal.IOKeys;
import io.pipelite.core.flow.execution.deadletter.DeadLetterQueueRepository;
import io.pipelite.core.flow.execution.deadletter.DeadLetteredExchangeFactory;
import io.pipelite.core.flow.internal.AbstractFlowNode;
import io.pipelite.core.flow.internal.Flow;
import io.pipelite.core.flow.internal.FlowNode;
import io.pipelite.dsl.barrier.Correlation;
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
    /** The ways to read a key that the barriers use for the flow that reads each queue. */
    private final Map<String, Set<Correlation>> correlationsByQueue = new ConcurrentHashMap<>();
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
        for (BarrierSpec.Await await : spec.getAwaits()) {
            correlationsByQueue.computeIfAbsent(await.queueName(), queue -> ConcurrentHashMap.newKeySet()).add(await.correlation());
        }
    }

    /** The distinct ways the barriers read a key from an execution of the flow that reads {@code queueName}. */
    public Set<Correlation> correlationsAwaiting(String queueName) {
        return Set.copyOf(correlationsByQueue.getOrDefault(queueName, Set.of()));
    }

    /**
     * An execution of a flow that some barrier awaits ended: tells what became of it for each of its keys. It tells
     * nothing for an execution that was retried or is waiting at a barrier of its own.
     */
    public void executionEnded(ExecutionTracker tracker) {
        tell(tracker, tracker.settle());
    }

    /** The retries of an execution were exhausted: it failed definitively. */
    public void executionExhausted(ExecutionTracker tracker) {
        tell(tracker, tracker.settleExhausted());
    }

    private void tell(ExecutionTracker tracker, ExecutionTracker.Outcome outcome) {
        switch (outcome) {
            case COMPLETED -> tracker.pendings().forEach(pending -> flowCompleted(pending.flow(), pending.key()));
            case FAILED -> tracker.pendings().forEach(pending -> flowFailed(pending.flow(), pending.key()));
            case NONE -> {
            }
        }
    }

    /**
     * What to run when the execution {@code tracker} follows ended, or {@code null} without a tracker. It never
     * throws: the flow that ended must not be affected by what the barriers do about it.
     */
    public Runnable endOf(ExecutionTracker tracker) {
        if (tracker == null) {
            return null;
        }
        return () -> {
            try {
                executionEnded(tracker);
            } catch (RuntimeException failure) {
                sysLogger.error("The barriers could not be told that an execution ended", failure);
            }
        };
    }

    /** {@code target} followed by what to run when it returned normally: for a resume that is the end of an execution. */
    public static FlowNode endingWith(String flowName, FlowNode target, Runnable onEnd) {
        if (onEnd == null) {
            return target;
        }
        final AbstractFlowNode wrapper = new AbstractFlowNode() {
            @Override
            public void process(ExchangeImpl exchange) {
                target.process(exchange);
                onEnd.run();
            }
        };
        wrapper.setFlowName(flowName);
        wrapper.setProcessorName(target.getProcessorName());
        return wrapper;
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

    /**
     * What the barriers need on a schedule: gives up the waits that took longer than their barrier allows (they
     * go to the dead letter queue), forgets the completions that are no longer needed, and resumes what is
     * resolved but was not resumed (a crash, or a worker pool that refused it).
     */
    public void maintain() {
        final BarrierRepository repository = repository();
        final Instant now = clock.get();
        repository.expire(now).forEach(this::resume);
        repository.purgeCompletions(now);
        recover();
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
            if (waiter.status() == Waiter.Status.EXPIRED) {
                giveUp(waiter);
                return;
            }
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
            final Consumer<ExchangeImpl> ending = resumed -> {
                action.accept(resumed);
                final Runnable end = endOf(ExecutionTracker.of(resumed));
                if (end != null) {
                    end.run();
                }
            };
            final ResumeNode target = new ResumeNode(ending);
            target.setFlowName(waiter.flowName());
            target.setProcessorName(waiter.nodeName());
            dispatcher.dispatch(flow.get(), target, exchange, () -> finished(waiter));
        } catch (RuntimeException exception) {
            sysLogger.error("Unable to resume the wait {}: it stays resolved, for the recovery", waiter.id(), exception);
            resuming.remove(waiter.id());
        }
    }

    /**
     * The exchange waited longer than its barrier allows: it goes to the dead letter queue, with the exception that
     * says what it was waiting for, and the flows that await its own flow are told it failed. It stays where it is
     * if there is no dead letter queue to take it, so that nothing is lost.
     */
    private void giveUp(Waiter waiter) {
        final DeadLetterQueueRepository deadLetterQueue = context.getDeadLetterQueueRepository();
        final DeadLetteredExchangeFactory entryFactory = context.getDeadLetterQueueEntryFactory();
        if (deadLetterQueue == null || entryFactory == null) {
            sysLogger.warn("Unable to give up the wait {}: there is no dead letter queue, it stays expired", waiter.id());
            resuming.remove(waiter.id());
            return;
        }
        final ExchangeImpl exchange = deserialize(waiter);
        exchange.setProperty(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, waiter.nodeName());
        if (exchange.getProperty(IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_PROPERTY_NAME, String.class) == null) {
            exchange.setProperty(IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_PROPERTY_NAME, waiter.flowName());
        }
        final BarrierTimeoutException timeout = new BarrierTimeoutException(waiter.barrierId(), waiter.pending(),
            Duration.between(waiter.createdAt(), waiter.expiresAt()));
        deadLetterQueue.save(entryFactory.create(timeout, exchange));
        sysLogger.warn("{}: the exchange went to the dead letter queue", timeout.getMessage());
        final ExecutionTracker tracker = ExecutionTracker.of(exchange);
        if (tracker != null) {
            executionExhausted(tracker);
        }
        finished(waiter);
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
