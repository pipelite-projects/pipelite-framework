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
import io.pipelite.core.flow.RetryChannelExceptionHandler;
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.deadletter.DeadLetterQueueRepository;
import io.pipelite.core.flow.execution.deadletter.DeadLetteredExchange;
import io.pipelite.core.flow.execution.dump.SerializedFlowExecutionDump;
import io.pipelite.common.support.serialization.BaseEncoding;
import io.pipelite.common.support.serialization.Base64ObjectSerializer;
import io.pipelite.common.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.common.support.serialization.ObjectSerializer;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.process.ExceptionHandler;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Decides whether a failed message has exhausted its configured retry attempts. On exhaustion,
 * routes to the flow's configured dead-letter channel (if any) instead of always dropping the
 * message — see {@code RetryChannelExceptionHandler}, which copies both {@code maxAttempts} and
 * the dead-letter URL onto every {@link FlowExecutionDump} it creates, since this filter is a
 * single instance shared by every flow's dumps and has no other way to know a given dump's
 * owning flow's configuration.
 */
/**
 * Package-private since #82: constructed only by {@link RetryChannelDefinitionFactory}, in this
 * same package.
 */
class RetryStrategyFilter implements Processor {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final PipeliteContext pipeliteContext;
    private final FlowExecutionDumpRepository dumpRepository;
    private final DeadLetterQueueRepository deadLetterQueueRepository;
    private final ByteArrayToObjectConverter converter;
    private final ObjectSerializer objectSerializer;

    RetryStrategyFilter(PipeliteContext pipeliteContext, FlowExecutionDumpRepository dumpRepository,
                         DeadLetterQueueRepository deadLetterQueueRepository) {
        Preconditions.notNull(pipeliteContext, "pipeliteContext is required and cannot be null");
        Preconditions.notNull(dumpRepository, "dumpRepository is required and cannot be null");
        Preconditions.notNull(deadLetterQueueRepository, "deadLetterQueueRepository is required and cannot be null");
        this.pipeliteContext = pipeliteContext;
        this.dumpRepository = dumpRepository;
        this.deadLetterQueueRepository = deadLetterQueueRepository;
        this.converter = new ByteArrayToObjectConverter();
        this.objectSerializer = new Base64ObjectSerializer();
    }

    @Override
    public void process(Exchange exchange, ProcessContribution contribution) {

        final FlowExecutionDump executionDump = exchange.getInputPayloadAs(FlowExecutionDump.class);
        if(executionDump.getAttemptNumber() > executionDump.getMaxAttempts()){

            contribution.stopExecution();

            switch (executionDump.getExhaustionAction()) {
                case DEAD_LETTER_FLOW -> routeToDeadLetterTarget(executionDump);
                case BUILT_IN_DLQ -> routeToBuiltInDeadLetterQueue(executionDump);
                case FLOW_EXCEPTION_HANDLER -> invokeFlowExceptionHandler(executionDump);
                case NONE -> {
                    if (sysLogger.isErrorEnabled()) {
                        // Same "make it visible, not silent" fix as GlobalDefaultExceptionHandler
                        // (#87) - reachable today only as a defensive fallback, since the DSL now
                        // requires every .withRetry(...) to declare an exhaustion action.
                        sysLogger.error("FlowExecutionDump {} discarded after exhausting {} attempt(s) - " +
                                "no exhaustion action is configured for this flow, so no further recovery was attempted",
                            executionDump.getId(), executionDump.getMaxAttempts());
                    }
                }
            }

            // Execution stops here either way - every outcome above is final for this dump, so
            // it is now safe to remove (see #58: removing any earlier, before the outcome was
            // known, is exactly the gap this filter exists to close).
            dumpRepository.remove(executionDump.getId());
        }

    }

    private void routeToDeadLetterTarget(FlowExecutionDump executionDump) {
        final String deadLetterFlowName = executionDump.getDeadLetterFlowName();
        final ExchangeImpl recoveredExchange = tryDecodeExchange(executionDump);
        if(recoveredExchange == null){
            if(sysLogger.isWarnEnabled()){
                sysLogger.warn("FlowExecutionDump {} has no recoverable exchange data, unable to route to dead letter flow '{}'",
                    executionDump.getId(), deadLetterFlowName);
            }
        } else {
            final Optional<Flow> deadLetterFlow = pipeliteContext.tryFindFlowByName(deadLetterFlowName);
            if(deadLetterFlow.isPresent()){
                deadLetterFlow.get().supply(recoveredExchange);
            } else if(sysLogger.isWarnEnabled()){
                sysLogger.warn("Dead letter flow '{}' is not registered, unable to route exchange for FlowExecutionDump {}",
                    deadLetterFlowName, executionDump.getId());
            }
        }
    }

    private void routeToBuiltInDeadLetterQueue(FlowExecutionDump executionDump) {
        final ExchangeImpl recoveredExchange = tryDecodeExchange(executionDump);
        if(recoveredExchange == null){
            if(sysLogger.isWarnEnabled()){
                sysLogger.warn("FlowExecutionDump {} has no recoverable exchange data, unable to route to the built-in dead letter queue",
                    executionDump.getId());
            }
            return;
        }
        // Built directly from executionDump/recoveredExchange rather than via
        // DeadLetteredExchangeFactory: that factory is designed for the first-failure capture
        // path and needs a live Throwable, which never survives here - AbstractFlowExecutionDump#
        // failureException is transient, only the formatted stackTrace string round-trips through
        // a saved/reloaded dump (see FlowExecutionDump's own Javadoc).
        final DeadLetteredExchange entry = DeadLetteredExchange.createNow(executionDump.getId(), executionDump.getFlowName());
        entry.setSourceEndpointResource(executionDump.getSourceEndpointResource());
        entry.setFailedProcessor(executionDump.getFailedProcessor());
        // FAILURE_EXCEPTION_TYPE_HEADER_NAME is stored as a live Class object (see
        // RetryChannelExceptionHandler#handleException), not a String - tryGetHeader(...) would
        // throw ClassCastException trying to read it as one.
        entry.setExceptionType(recoveredExchange.tryGetHeaderAs(IOKeys.FAILURE_EXCEPTION_TYPE_HEADER_NAME, Class.class)
            .map(Class::getName).orElse(null));
        entry.setExceptionMessage(recoveredExchange.tryGetHeader(IOKeys.FAILURE_EXCEPTION_MESSAGE_HEADER_NAME).orElse(null));
        entry.setStackTrace(executionDump.getStackTrace());
        final String serializedExchangeData = objectSerializer.serializeObject(recoveredExchange);
        entry.setExchangeData(serializedExchangeData, objectSerializer.getEncoding());
        deadLetterQueueRepository.save(entry);
    }

    private void invokeFlowExceptionHandler(FlowExecutionDump executionDump) {
        final ExchangeImpl recoveredExchange = tryDecodeExchange(executionDump);
        if(recoveredExchange == null){
            if(sysLogger.isWarnEnabled()){
                sysLogger.warn("FlowExecutionDump {} has no recoverable exchange data, unable to invoke the flow's custom exception handler",
                    executionDump.getId());
            }
            return;
        }
        // The custom ExceptionHandler itself is never serialized into the dump (it's often a
        // lambda; requiring Serializable would be a new, unwelcome constraint) - instead the
        // current Flow is re-resolved by name and its currently-configured ExceptionHandler is
        // read fresh. Same assumption PipeliteFlowExecutionDumpSurvivesRestartIntegrationTest
        // already relies on: a flow's DSL definition is re-declared identically on every process
        // start, restart included.
        final Optional<FlowDefinition> flowDefinition = pipeliteContext.getFlowDefinition(executionDump.getFlowName());
        final RetryChannelExceptionHandler handler = flowDefinition
            .map(definition -> definition.<RetryChannelExceptionHandler>getExceptionHandler(RetryChannelExceptionHandler.class))
            .orElse(null);
        final ExceptionHandler exhaustionExceptionHandler = handler != null ? handler.getExhaustionExceptionHandler() : null;
        if (exhaustionExceptionHandler == null) {
            if (sysLogger.isWarnEnabled()) {
                sysLogger.warn("Flow '{}' has no currently-configured exhaustion ExceptionHandler, unable to invoke it for FlowExecutionDump {}",
                    executionDump.getFlowName(), executionDump.getId());
            }
            return;
        }
        // The original Throwable never survives (transient, see routeToBuiltInDeadLetterQueue's
        // own comment) - reconstructed as a plain RuntimeException carrying the recorded stack
        // trace text, so the custom handler still has something meaningful to log.
        final Throwable reconstructedException = new RuntimeException(
            "Retry attempts exhausted - original stack trace:\n" + executionDump.getStackTrace());
        exhaustionExceptionHandler.handleException(reconstructedException, recoveredExchange);
    }

    private ExchangeImpl tryDecodeExchange(FlowExecutionDump executionDump) {
        if(!(executionDump instanceof SerializedFlowExecutionDump)){
            return null;
        }
        final SerializedFlowExecutionDump serialized = (SerializedFlowExecutionDump) executionDump;
        if(serialized.getExchangeData() == null){
            return null;
        }
        final byte[] exchangeContent = BaseEncoding.base64().decode(serialized.getExchangeData());
        return converter.convert(exchangeContent, ExchangeImpl.class);
    }

}
