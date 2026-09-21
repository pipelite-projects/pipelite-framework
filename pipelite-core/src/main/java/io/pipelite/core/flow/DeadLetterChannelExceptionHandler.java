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
import io.pipelite.core.context.internal.DeclaredDestination;
import io.pipelite.core.context.internal.DeclaresDestinations;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.process.ExceptionHandler;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.ExchangeImpl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;

/**
 * Used when a flow declares {@code .withErrorChannel(err -> err.toChannel(target))} without also
 * declaring a retry (issue #91: {@code .withRetry(...)}): routes straight to the dead-letter
 * target on the very first unhandled failure, no retry attempted. (When retry is also declared,
 * it runs first instead — see {@code RetryChannelExceptionHandler}/{@code RetryStrategyFilter},
 * which carry the same target through a {@code FlowExecutionDump} and route to it only once
 * attempts are exhausted.) {@code deadLetterTarget} is a URL, delivered through {@code
 * PipeliteContext.supplyExchange(...)}: {@code link://<source endpoint name>} for an internal
 * flow, or any registered channel adapter's protocol for an external system. Since issue #102 it
 * is never a flow name: the flow name is an identity, not an address.
 */
public class DeadLetterChannelExceptionHandler implements ExceptionHandler, PipeliteContextAware, DeclaresDestinations {

    private final String deadLetterTarget;

    private PipeliteContext pipeliteContext;

    public DeadLetterChannelExceptionHandler(String deadLetterTarget) {
        Preconditions.hasText(deadLetterTarget, "deadLetterTarget is required and cannot be null/empty");
        this.deadLetterTarget = deadLetterTarget;
    }

    @Override
    public Collection<DeclaredDestination> declaredDestinations() {
        return List.of(new DeclaredDestination(deadLetterTarget, "toChannel(...)"));
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

        // The target is always a URL (issue #102): the same ChannelURL -> ChannelAdapter ->
        // Endpoint -> Producer resolution reaches an internal flow (link://) and an external
        // system alike, and RetryStrategyFilter delivers a retried exchange exactly the same way.
        pipeliteContext.supplyExchange(deadLetterTarget, exchangeImpl);

    }

    private static String formatStackTrace(Throwable throwable) {
        final StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

}
