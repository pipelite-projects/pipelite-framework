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
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.process.ExceptionHandler;
import io.pipelite.spi.channel.ChannelURL;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

/**
 * Used when a flow declares {@code .withErrorChannel(err -> err.toChannel(target))} without also
 * declaring a retry (issue #91: {@code .withRetry(...)}): routes straight to the dead-letter
 * target on the very first unhandled failure, no retry attempted. (When retry is also declared,
 * it runs first instead — see {@code RetryChannelExceptionHandler}/{@code RetryStrategyFilter},
 * which carry the same target through a {@code FlowExecutionDump} and route to it only once
 * attempts are exhausted.) {@code deadLetterTarget} is either a bare flow name - resolved via
 * {@code PipeliteContext.tryFindFlowByName(...)}, not by its {@code fromSource(...)} resource,
 * which may be a different string entirely - or a protocol-qualified channel adapter URL,
 * delivered directly via {@code PipeliteContext.supplyExchange(...)}'s own protocol resolution,
 * with no {@code Flow} required to receive it.
 */
public class DeadLetterChannelExceptionHandler implements ExceptionHandler, PipeliteContextAware {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final String deadLetterTarget;

    private PipeliteContext pipeliteContext;

    public DeadLetterChannelExceptionHandler(String deadLetterTarget) {
        Preconditions.hasText(deadLetterTarget, "deadLetterTarget is required and cannot be null/empty");
        this.deadLetterTarget = deadLetterTarget;
    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        this.pipeliteContext = pipeliteContext;
    }

    @Override
    public void handleException(Throwable failureException, Exchange exchange) {

        Preconditions.notNull(pipeliteContext, "pipeliteContext is required and cannot be null");

        // Downcast is safe here: this handler is only ever wired by FlowDefinitionBuilder for
        // internal use and always invoked with a real Exchange (see issue #91).
        final ExchangeImpl exchangeImpl = (ExchangeImpl) exchange;

        exchangeImpl.putHeader(IOKeys.FAILURE_EXCEPTION_TYPE_HEADER_NAME, failureException.getClass());
        exchangeImpl.putHeader(IOKeys.FAILURE_EXCEPTION_MESSAGE_HEADER_NAME, failureException.getMessage());
        exchangeImpl.putHeader(IOKeys.FAILURE_STACK_TRACE_HEADER_NAME, formatStackTrace(failureException));

        if (ChannelURL.parse(deadLetterTarget).hasProtocol()) {
            // Reuses PipeliteContext.supplyExchange(...)'s own protocol branch as-is - same
            // ChannelURL -> ChannelAdapter -> Endpoint -> Producer resolution, no Flow required.
            pipeliteContext.supplyExchange(deadLetterTarget, exchangeImpl);
            return;
        }

        final Optional<Flow> deadLetterFlow = pipeliteContext.tryFindFlowByName(deadLetterTarget);
        if(deadLetterFlow.isPresent()){
            deadLetterFlow.get().supply(exchangeImpl);
        } else if(sysLogger.isWarnEnabled()){
            sysLogger.warn("Dead letter flow '{}' is not registered, unable to route exchange", deadLetterTarget);
        }

    }

    private static String formatStackTrace(Throwable throwable) {
        final StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

}
