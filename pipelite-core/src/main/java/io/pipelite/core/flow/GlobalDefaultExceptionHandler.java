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

import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.ExceptionHandler;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The fallback {@link ExceptionHandler} for a flow that declares neither {@code
 * .withRetryChannel(...)} nor {@code .withErrorChannel(...)} (issue #87) - previously an empty,
 * unwired no-op, which meant "no handler configured" actually meant "no handler at all"
 * ({@code exceptionHandler == null} throughout {@code AbstractProcessorNode}/{@code
 * DefaultConsumer}/{@code SplitterNode}), with two consequences: (1) inconsistent behavior
 * depending on which layer threw - a processor-level failure propagated and was only caught (and
 * logged a second time) at the dispatch-thread level, while a consumer-level failure was quietly
 * swallowed one layer up; and (2) for the processor case, the exchange's durable-inbox entry
 * (issue #70) was never acknowledged, since the propagating exception meant {@code
 * EventDrivenConsumer#dispatchToNext} never reached its own {@code acknowledge(...)} call - so
 * with the inbox enabled (the default), the exact same failure would be retried again on every
 * future application restart, forever, with no dead-letter escape valve.
 * <p>
 * Being a genuine {@link ExceptionHandler} that returns normally (like {@code
 * DeadLetterChannelExceptionHandler}/{@code RetryChannelExceptionHandler} already do) fixes both:
 * every layer now goes through the same "handled, not rethrown" path, and the durable-inbox entry
 * gets acknowledged like any other resolved outcome. The trade-off this makes explicit: a flow
 * with no error handling configured gets exactly one attempt, then the exchange is dropped -
 * logged here, not silently. That is the intentional default; opt into {@code
 * .withRetryChannel(...)}/{@code .withErrorChannel(...)} for anything more than that.
 */
public class GlobalDefaultExceptionHandler implements ExceptionHandler {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    public GlobalDefaultExceptionHandler() {
    }

    @Override
    public void handleException(Throwable exception, IOContext ioContext) {
        // Downcast is safe here: this handler is only ever wired by FlowDefinitionBuilder for
        // internal use and always invoked with a real Exchange (see issue #91).
        final Exchange exchange = (Exchange) ioContext;
        if (sysLogger.isErrorEnabled()) {
            // Only ever set by AbstractProcessorNode/SplitterNode - absent for a failure at the
            // consumer's own enqueue step (see this class's own Javadoc), the one case where no
            // single processor is "the" failing step.
            final String failedProcessor = exchange.getProperty(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, String.class);
            final String failedAt = failedProcessor != null ? String.format("processor '%s'", failedProcessor) : "the consumer";
            sysLogger.error("Exchange '{}' discarded after an unhandled exception in {} - " +
                    "no retry channel or error channel is configured for this flow, so no recovery was attempted",
                exchange.getInput().getId(), failedAt, exception);
        }
    }

}
