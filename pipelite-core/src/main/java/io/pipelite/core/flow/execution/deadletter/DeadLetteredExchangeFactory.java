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
import io.pipelite.common.support.serialization.ObjectSerializer;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.IdentityGenerator;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Mirrors {@code FlowExecutionDumpFactory} (issue #93): serializes the whole {@code Exchange} via
 * the same injected {@link ObjectSerializer} - the same existing constraint applies, the
 * {@code Exchange} payload must be {@code Serializable}.
 */
public class DeadLetteredExchangeFactory {

    private final IdentityGenerator identityGenerator;
    private final ObjectSerializer objectSerializer;

    public DeadLetteredExchangeFactory(IdentityGenerator identityGenerator, ObjectSerializer objectSerializer) {
        Preconditions.notNull(identityGenerator, "identityGenerator is required and cannot be null");
        Preconditions.notNull(objectSerializer, "objectSerializer is required and cannot be null");
        this.identityGenerator = identityGenerator;
        this.objectSerializer = objectSerializer;
    }

    public DeadLetteredExchange create(Throwable failureException, Exchange exchange) {

        final String id = identityGenerator.nextIdAsText();

        final String flowName = exchange.getPropertyOrDefault(
            IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_PROPERTY_NAME, String.class, null);
        Preconditions.notNull(flowName, String.format("Unable to resolve exchange property '%s'",
            IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_PROPERTY_NAME));

        final String sourceEndpointResource = exchange.getPropertyOrDefault(
            IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_SOURCE_ENDPOINT_RESOURCE_PROPERTY_NAME, String.class, null);

        // Optional, same as FlowExecutionDumpFactory's own failedProcessor: absent when the
        // failure originated at the consumer itself rather than inside a processor's own catch
        // block.
        final String failedProcessor = exchange.getPropertyOrDefault(
            IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, String.class, null);

        final DeadLetteredExchange entry = DeadLetteredExchange.createNow(id, flowName);
        entry.setSourceEndpointResource(sourceEndpointResource);
        entry.setFailedProcessor(failedProcessor);
        entry.setExceptionType(failureException.getClass().getName());
        entry.setExceptionMessage(failureException.getMessage());
        entry.setStackTrace(formatStackTrace(failureException));

        final String serializedExchangeData = objectSerializer.serializeObject(exchange);
        entry.setExchangeData(serializedExchangeData, objectSerializer.getEncoding());

        return entry;
    }

    private static String formatStackTrace(Throwable throwable) {
        final StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));
        return stringWriter.toString();
    }

}
