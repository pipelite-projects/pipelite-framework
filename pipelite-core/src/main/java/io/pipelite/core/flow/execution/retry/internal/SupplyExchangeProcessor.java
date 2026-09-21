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
package io.pipelite.core.flow.execution.retry.internal;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.flow.FlowNodeLocator;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.dump.SerializedFlowExecutionDump;
import io.pipelite.common.support.serialization.BaseEncoding;
import io.pipelite.common.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.endpoint.EventDrivenConsumerService;
import io.pipelite.spi.flow.AbstractFlowNode;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Resumes a failed flow's execution. Prefers resuming directly at the {@code FlowNode} that
 * actually failed ({@link SerializedFlowExecutionDump#getFailedProcessor()}) — bypassing the
 * flow's source and every already-succeeded step, which would otherwise re-run and could
 * re-apply side effects. Falls back to the pre-existing behavior (resupply from the flow's own
 * source) only when the failed processor's identity or the flow itself can't be resolved, e.g.
 * a dump produced before this resume mechanism existed, or a failure that didn't originate
 * inside a processor's own catch block.
 * <p>
 * The direct-resume path runs through the target flow's own {@link EventDrivenConsumerService}
 * (its {@link io.pipelite.spi.endpoint} dispatch strategy) when the target flow has one, instead
 * of always running on the retry-channel's own thread — see issue #61: a retry previously ran
 * entirely outside the target flow's own {@code concurrency(n)} budget, serialized on {@code
 * RetryService}'s single thread regardless of how much parallelism that flow was actually
 * configured for. Falls back to a direct, ungated call when the target flow's consumer isn't an
 * {@code EventDrivenConsumerService} (e.g. a {@code ScheduledPollingConsumerService}-based source
 * such as {@code file://}, or an adapter like Kafka's that deliberately bypasses its own dispatch
 * strategy per issue #64) — there is no {@code concurrency(n)} budget to inherit there either way.
 * <p>
 * Also owns removing the resumed {@code FlowExecutionDump} from {@code dumpRepository} — not
 * {@code ResolveExecutionDumpProcessor}, which only resolves it (see #58) — but only once the
 * attempt's outcome is actually settled (success, or a new dump already durably saved by {@code
 * RetryChannelExceptionHandler} for a further attempt), via the {@code onComplete} callback passed
 * through {@link #dispatch}. That matters precisely because the direct-resume path is <strong>not
 * always synchronous</strong> once issue #61's dispatch-through-the-flow's-own-budget change is in
 * play: a {@code PooledDispatchStrategy}-backed target flow returns from dispatch as soon as the
 * work is submitted, not once it finishes, so removing the dump right after that call would return
 * would reopen exactly the #58 crash window this class exists to close. The ungated fallback call
 * (target flow has no {@code EventDrivenConsumerService}) and the source-resupply fallback below
 * (via {@code pipeliteContext.supplyExchange(...)}, used only when the failed processor or its
 * flow can't be resolved) remove immediately after their own synchronous call returns — the latter
 * is not equally safe in principle (it merely enqueues the exchange on the target flow's own
 * in-memory consumer and returns immediately, the same "accepted but not yet actually processed"
 * gap issue #70 (durable inbox) exists to close generically), removed anyway for consistency with
 * today's behavior on this narrow, already-degraded path, which still carries a smaller version of
 * #58's original crash window until #70 lands.
 * <p>
 * Before doing anything else, claims the dump via {@link FlowExecutionDumpRepository#tryClaim}
 * (returning immediately, without dispatching, if that fails) — guards a race issue #61's own
 * batch-drain loop opens up against this deferred-removal design: {@code dumpRepository.poll()}
 * only ever returns a still-{@code PENDING} dump, but {@code ScheduledPollingConsumerService}
 * drains up to {@code batchSize} pending dumps in a tight loop, calling {@code receive()} again
 * immediately after handing one off — with no wait for that hand-off to actually finish. Without
 * claiming first, the same still-pending dump would be peeked and dispatched again on the very
 * next loop iteration, concurrently with its own first attempt still running, every time that
 * attempt hadn't finished (and therefore removed itself) within the few microseconds before the
 * next {@code receive()} call — verified to reliably over-fire a target's processing several times
 * per batch in practice, not just a theoretical race. The claim also correctly excludes a dump
 * from a <em>different</em> caller's {@code poll()} — another thread in this JVM, or (for a shared,
 * durable repository) another process instance entirely — since it lives on the dump itself, not
 * in something local to this class.
 */
/**
 * Package-private since #82: constructed only by {@link RetryChannelDefinitionFactory}, in this
 * same package.
 */
class SupplyExchangeProcessor extends AbstractFlowNode implements PipeliteContextAware {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final ByteArrayToObjectConverter converter;
    private final FlowExecutionDumpRepository dumpRepository;

    private PipeliteContext pipeliteContext;

    SupplyExchangeProcessor(FlowExecutionDumpRepository dumpRepository) {
        Preconditions.notNull(dumpRepository, "dumpRepository is required and cannot be null");
        this.dumpRepository = dumpRepository;
        this.converter = new ByteArrayToObjectConverter();
    }

    @Override
    public void process(ExchangeImpl exchange) {

        final SerializedFlowExecutionDump executionDump = exchange.getInputPayloadAs(SerializedFlowExecutionDump.class);
        final String dumpId = executionDump.getId();

        if (!dumpRepository.tryClaim(dumpId)) {
            // Already claimed by an earlier delivery of this same dump that hasn't finished (and
            // therefore hasn't removed it) yet - see this class's own Javadoc and
            // FlowExecutionDumpRepository#tryClaim. Skip silently: the claim owner already owns
            // this dump's eventual removal/outcome, and poll() itself won't offer this dump again
            // to anyone while the claim stands.
            if (sysLogger.isDebugEnabled()) {
                sysLogger.debug("FlowExecutionDump {} is already claimed, skipping duplicate delivery", dumpId);
            }
            return;
        }

        final String exchangeData = executionDump.getExchangeData();
        final byte[] exchangeContent = BaseEncoding.base64().decode(exchangeData);

        final ExchangeImpl recoveredExchange = converter.convert(exchangeContent, ExchangeImpl.class);
        recoveredExchange.setProperty(IOKeys.FLOW_EXECUTION_ATTEMPT_NUMBER_PROPERTY_NAME, executionDump.getAttemptNumber());
        recoveredExchange.setProperty(IOKeys.FLOW_EXECUTION_LAST_EXECUTED_PROCESSOR_PROPERTY_NAME, executionDump.getLastExecutedProcessor());

        final String failedProcessor = executionDump.getFailedProcessor();
        if (failedProcessor != null) {
            final Optional<Flow> flowHolder = pipeliteContext.tryFindFlow(executionDump.getSourceEndpointResource());
            final FlowNode target = flowHolder
                .flatMap(flow -> FlowNodeLocator.findByProcessorName(flow, failedProcessor))
                .orElse(null);
            if (target != null) {
                // Removal happens inside the onComplete callback, not right after dispatch(...)
                // returns - dispatch is not always synchronous (see this class's own Javadoc and
                // DispatchStrategy#dispatch), so removing here unconditionally would reopen #58's
                // crash window whenever the target flow dispatches through a pooled strategy.
                dispatch(flowHolder.get(), target, recoveredExchange, () -> dumpRepository.remove(dumpId));
                return;
            }
            if (sysLogger.isWarnEnabled()) {
                sysLogger.warn("Unable to resolve failed processor '{}' on flow source '{}' for FlowExecutionDump {}, " +
                        "falling back to source resupply",
                    failedProcessor, executionDump.getSourceEndpointResource(), executionDump.getId());
            }
        } else if (sysLogger.isWarnEnabled()) {
            sysLogger.warn("FlowExecutionDump {} has no failedProcessor, falling back to source resupply",
                executionDump.getId());
        }

        final String endpointURL = String.format("link://%s", executionDump.getSourceEndpointResource());
        pipeliteContext.supplyExchange(endpointURL, recoveredExchange);
        // Not equally safe: supplyExchange(...) only enqueues on the target flow's own in-memory
        // consumer here (see this class's own Javadoc) - removed anyway for consistency with
        // today's behavior on this narrow, already-degraded fallback path.
        dumpRepository.remove(dumpId);
    }

    /**
     * Runs {@code target} through the target flow's own {@code EventDrivenConsumerService} —
     * under its {@code concurrency(n)} budget — when it has one, so a resumed retry competes for
     * the same permits as fresh messages instead of running for free, serialized on the
     * retry-channel's own thread. That path is generally asynchronous (see {@code
     * DispatchStrategy#dispatch}) — returns once submitted, not once {@code target} has actually
     * finished, so several pending retries can genuinely be in flight at once instead of one at a
     * time. The fallback path (no {@code EventDrivenConsumerService}, nothing to inherit
     * concurrency from) remains a direct, synchronous call, same as before issue #61.
     * <p>
     * {@code onComplete} runs exactly once, only once {@code target}'s processing has actually
     * finished (success or a handled exception) on whichever thread that turns out to be — never
     * merely once this method or the underlying dispatch call returns. Callers relying on {@code
     * onComplete} for anything outcome-dependent (e.g. removing a retry-channel dump, see #58)
     * must not assume synchronous completion just because this method itself already returned.
     */
    private static void dispatch(Flow flow, FlowNode target, ExchangeImpl exchange, Runnable onComplete) {
        if (flow.isConsumerOfType(EventDrivenConsumerService.class)) {
            flow.getConsumerAs(EventDrivenConsumerService.class).dispatchToNode(target, exchange, onComplete);
        } else {
            try {
                target.process(exchange);
            } finally {
                onComplete.run();
            }
        }
    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        this.pipeliteContext = pipeliteContext;
    }
}
