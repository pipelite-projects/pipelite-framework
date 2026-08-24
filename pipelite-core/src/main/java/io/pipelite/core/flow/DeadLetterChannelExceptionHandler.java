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
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.exchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

/**
 * Used when a flow declares {@code .withErrorChannel(c -> c.definedFlow(flowName))} without also
 * declaring {@code .withRetryChannel(...)}: routes straight to the dead-letter flow on the
 * very first unhandled failure, no retry attempted. (When both are declared, retry runs first
 * instead — see {@code RetryChannelExceptionHandler}/{@code RetryStrategyFilter}, which carry
 * the same flow name through a {@code FlowExecutionDump} and route to it only once attempts are
 * exhausted.) {@code deadLetterFlowName} identifies the target by its own {@code
 * Pipelite.defineFlow(...)} name, resolved via {@code PipeliteContext.tryFindFlowByName(...)} —
 * not by its {@code fromSource(...)} resource, which may be a different string entirely.
 */
public class DeadLetterChannelExceptionHandler implements ExceptionHandler, PipeliteContextAware {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final String deadLetterFlowName;

    private PipeliteContext pipeliteContext;

    public DeadLetterChannelExceptionHandler(String deadLetterFlowName) {
        Preconditions.hasText(deadLetterFlowName, "deadLetterFlowName is required and cannot be null/empty");
        this.deadLetterFlowName = deadLetterFlowName;
    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        this.pipeliteContext = pipeliteContext;
    }

    @Override
    public void handleException(Throwable failureException, Exchange exchange) {

        Preconditions.notNull(pipeliteContext, "pipeliteContext is required and cannot be null");

        exchange.putHeader(IOKeys.FAILURE_EXCEPTION_TYPE_HEADER_NAME, failureException.getClass());
        exchange.putHeader(IOKeys.FAILURE_EXCEPTION_MESSAGE_HEADER_NAME, failureException.getMessage());
        exchange.putHeader(IOKeys.FAILURE_STACK_TRACE_HEADER_NAME, formatStackTrace(failureException));

        final Optional<Flow> deadLetterFlow = pipeliteContext.tryFindFlowByName(deadLetterFlowName);
        if(deadLetterFlow.isPresent()){
            deadLetterFlow.get().supply(exchange);
        } else if(sysLogger.isWarnEnabled()){
            sysLogger.warn("Dead letter flow '{}' is not registered, unable to route exchange", deadLetterFlowName);
        }

    }

    private static String formatStackTrace(Throwable throwable) {
        final StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

}
