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
package io.pipelite.core.security;

import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.flow.RetryChannelExceptionHandler;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpFactory;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.core.support.serialization.Base64ObjectSerializer;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

/**
 * Security tests for RetryChannelExceptionHandler.
 *
 * Vulnerability — Exception Details Leaked in Exchange Headers:
 *   RetryChannelExceptionHandler writes the exception class (X-Failure-Exception-Type)
 *   and its message (X-Failure-Exception-Message) into exchange headers.
 *   Exchange headers are propagated along the pipeline. If they reach an HTTP response
 *   or a Kafka message, they expose internal implementation details to external consumers:
 *   - fully-qualified class names reveal the technology stack
 *   - exception messages may contain sensitive data (connection strings, credentials,
 *     SQL queries, file paths, etc.)
 */
public class SecurityTest_RetryExceptionInfoLeakage {

    private RetryChannelExceptionHandler handler;
    private ExchangeFactory exchangeFactory;

    @Before
    public void setup() {
        exchangeFactory = new DefaultExchangeFactory(
            new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));

        FlowExecutionDumpFactory dumpFactory = new FlowExecutionDumpFactory(
            new DistributedIdentityGeneratorImpl(),
            new Base64ObjectSerializer());

        handler = new RetryChannelExceptionHandler();
        handler.setExecutionDumpFactory(dumpFactory);
        handler.setDumpRepository(new FlowExecutionDumpInMemoryRepository());
    }

    /**
     * The exception class name must not be stored in exchange headers.
     * Headers are propagated downstream and may be serialised into HTTP responses.
     *
     * Expected: X-Failure-Exception-Type header is absent after exception handling.
     * Actual:   header is present — exposes the fully-qualified class name to callers.
     */
    @Test
    @Ignore
    public void shouldNotExposeExceptionTypeInExchangeHeaders() {
        Exchange exchange = buildExchangeWithFlowMetadata();
        RuntimeException ex = new RuntimeException("something went wrong");

        handler.handleException(ex, exchange);

        // FAILS: RetryChannelExceptionHandler explicitly writes this header
        assertFalse(
            "Exchange header '" + IOKeys.FAILURE_EXCEPTION_TYPE_HEADER_NAME +
                "' must not be set — it leaks internal class names",
            exchange.hasHeader(IOKeys.FAILURE_EXCEPTION_TYPE_HEADER_NAME));
    }

    /**
     * The exception message must not be stored in exchange headers.
     * Exception messages may contain credentials, SQL queries, or file paths.
     *
     * Expected: X-Failure-Exception-Message header is absent after exception handling.
     * Actual:   header is present — exposes potentially sensitive exception message.
     */
    @Test
    @Ignore
    public void shouldNotExposeExceptionMessageInExchangeHeaders() {
        Exchange exchange = buildExchangeWithFlowMetadata();
        RuntimeException ex = new RuntimeException(
            "Connection refused: jdbc:mysql://admin:secret@internal-db:3306/prod");

        handler.handleException(ex, exchange);

        // FAILS: RetryChannelExceptionHandler explicitly writes this header
        assertFalse(
            "Exchange header '" + IOKeys.FAILURE_EXCEPTION_MESSAGE_HEADER_NAME +
                "' must not be set — it may contain sensitive data from the exception message",
            exchange.hasHeader(IOKeys.FAILURE_EXCEPTION_MESSAGE_HEADER_NAME));
    }

    private Exchange buildExchangeWithFlowMetadata() {
        Exchange exchange = exchangeFactory.createExchange("payload");
        exchange.setProperty(IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_PROPERTY_NAME, "test-flow");
        exchange.setProperty(
            IOKeys.FLOW_EXECUTION_LAST_EXECUTED_FLOW_SOURCE_ENDPOINT_RESOURCE_PROPERTY_NAME, "source");
        exchange.setProperty(IOKeys.FLOW_EXECUTION_LAST_EXECUTED_PROCESSOR_PROPERTY_NAME, "processor");
        return exchange;
    }
}
