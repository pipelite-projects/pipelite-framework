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
package io.pipelite.core.flow.execution.deadletter;

import io.pipelite.common.support.Preconditions;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.ExceptionHandler;
import io.pipelite.spi.flow.exchange.Exchange;

/**
 * Used when a flow declares {@code .withErrorChannel(err -> err.toDLQ())} without also declaring
 * a retry (issue #93): routes straight to the framework's built-in dead letter queue on the very
 * first unhandled failure, no retry attempted, no {@code Flow} lookup at all - unlike {@code
 * DeadLetterChannelExceptionHandler}, which this otherwise mirrors.
 */
public class DeadLetterQueueExceptionHandler implements ExceptionHandler {

    private DeadLetteredExchangeFactory entryFactory;
    private DeadLetterQueueRepository repository;

    public void setEntryFactory(DeadLetteredExchangeFactory entryFactory) {
        if (this.entryFactory == null) {
            this.entryFactory = entryFactory;
        }
    }

    public void setRepository(DeadLetterQueueRepository repository) {
        if (this.repository == null) {
            this.repository = repository;
        }
    }

    @Override
    public void handleException(Throwable failureException, IOContext ioContext) {

        Preconditions.notNull(entryFactory, "entryFactory is required and cannot be null");
        Preconditions.notNull(repository, "repository is required and cannot be null");

        // Downcast is safe here: this handler is only ever wired by FlowDefinitionBuilder for
        // internal use and always invoked with a real Exchange (see issue #91).
        final Exchange exchange = (Exchange) ioContext;

        final DeadLetteredExchange entry = entryFactory.create(failureException, exchange);
        repository.save(entry);
    }

}
