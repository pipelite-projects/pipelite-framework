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
package io.pipelite.core.flow;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.context.internal.DeclaredDestination;
import io.pipelite.core.context.internal.DeclaresDestinations;
import io.pipelite.core.definition.builder.retry.RetryBuilder;
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpFactory;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.definition.builder.Backoff;
import io.pipelite.dsl.process.ExceptionHandler;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public class RetryChannelExceptionHandler implements ExceptionHandler, DeclaresDestinations {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private FlowExecutionDumpFactory executionDumpFactory;
    private FlowExecutionDumpRepository dumpRepository;

    // Config, not shared infra: plain assignment (not idempotent-guarded like the two fields
    // above) is fine — set once by FlowDefinitionBuilder.build() from user DSL config, never
    // re-set afterward.
    private int maxAttempts = RetryBuilder.DEFAULT_MAX_ATTEMPTS;
    private Backoff backoff;
    private FlowExecutionDump.ExhaustionAction exhaustionAction = FlowExecutionDump.ExhaustionAction.NONE;
    private String deadLetterTarget;

    // Not copied onto FlowExecutionDump (custom handlers are often lambdas, not Serializable) -
    // re-read fresh from this same, currently-registered handler by RetryStrategyFilter at
    // exhaustion time instead. See FlowExecutionDump.ExhaustionAction#FLOW_EXCEPTION_HANDLER.
    private ExceptionHandler exhaustionExceptionHandler;

    public RetryChannelExceptionHandler() {
    }

    public void setExecutionDumpFactory(FlowExecutionDumpFactory executionDumpFactory) {
        if(this.executionDumpFactory == null){
            this.executionDumpFactory = executionDumpFactory;
        }
    }

    public void setDumpRepository(FlowExecutionDumpRepository dumpRepository) {
        if(this.dumpRepository == null) {
            this.dumpRepository = dumpRepository;
        }
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    /**
     * The delay to honor between attempts (issue #95) - {@code null} (the default) means retry as
     * soon as the retry channel polls, same as before this existed.
     */
    public void setBackoff(Backoff backoff) {
        this.backoff = backoff;
    }

    /**
     * The action {@link io.pipelite.core.flow.execution.retry.internal.RetryStrategyFilter} takes
     * once attempts are exhausted (issue #91) - copied onto every {@link FlowExecutionDump} this
     * handler creates, the same way {@link #setMaxAttempts(int)} always has been, since that
     * filter is a single shared instance serving every flow's dumps.
     */
    public void setExhaustionAction(FlowExecutionDump.ExhaustionAction exhaustionAction) {
        this.exhaustionAction = Preconditions.notNull(exhaustionAction, "exhaustionAction is required and cannot be null");
    }

    public void setDeadLetterTarget(String deadLetterTarget) {
        this.deadLetterTarget = deadLetterTarget;
    }

    public void setExhaustionExceptionHandler(ExceptionHandler exhaustionExceptionHandler) {
        this.exhaustionExceptionHandler = exhaustionExceptionHandler;
    }

    public ExceptionHandler getExhaustionExceptionHandler() {
        return exhaustionExceptionHandler;
    }

    /**
     * The dead-letter target, when exhausted attempts are routed to one (issue #88): what the startup
     * validation reads. A retry that ends in the built-in queue or a custom handler declares none.
     */
    @Override
    public Collection<DeclaredDestination> declaredDestinations() {
        if (exhaustionAction == FlowExecutionDump.ExhaustionAction.DEAD_LETTER_CHANNEL) {
            return List.of(new DeclaredDestination(deadLetterTarget, "withRetry(...) onErrorChannel(toChannel(...))"));
        }
        return List.of();
    }


    @Override
    public void handleException(Throwable failureException, Exchange exchange) {

        Preconditions.notNull(executionDumpFactory, "executionDumpFactory is required and cannot be null");
        Preconditions.notNull(dumpRepository, "dumpRepository is required and cannot be null");

        // Downcast is safe here: this handler is only ever wired by FlowDefinitionBuilder for
        // internal use and always invoked with a real Exchange (see issue #91).
        final ExchangeImpl exchangeImpl = (ExchangeImpl) exchange;

        exchangeImpl.putHeader(IOKeys.FAILURE_EXCEPTION_TYPE_HEADER_NAME, failureException.getClass());
        exchangeImpl.putHeader(IOKeys.FAILURE_EXCEPTION_MESSAGE_HEADER_NAME, failureException.getMessage());

        // executionDumpFactory.create(...) already reads FLOW_EXECUTION_ATTEMPT_NUMBER_PROPERTY_NAME
        // off the exchange and sets attemptNumber+1 on the dump it returns — a previous version of
        // this method re-read the same (un-incremented) property afterward and called
        // executionDump.setAttemptNumber(attemptNumber) here, silently overwriting the correct
        // increment. That bug meant attemptNumber never actually advanced across retries, so
        // RetryStrategyFilter's cap never triggered. Fixed by not re-setting it.
        final FlowExecutionDump executionDump = executionDumpFactory.create(failureException, exchangeImpl);
        executionDump.setStackTrace(formatStackTrace(failureException));
        executionDump.setMaxAttempts(maxAttempts);
        executionDump.setExhaustionAction(exhaustionAction);
        executionDump.setDeadLetterTarget(deadLetterTarget);
        if (backoff != null) {
            executionDump.setNextAttemptTime(LocalDateTime.now().plus(backoff.delayBeforeAttempt(executionDump.getAttemptNumber())));
        }

        final String executionDumpId = executionDump.getId();
        exchangeImpl.setProperty(IOKeys.FLOW_EXECUTION_DUMP_ID_PROPERTY_NAME, executionDumpId);

        dumpRepository.save(executionDump);

    }

    private static String formatStackTrace(Throwable throwable) {
        final StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

}
