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
package io.pipelite.spi.endpoint;

import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.process.ExceptionHandler;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import org.junit.Assert;
import org.junit.Test;

/**
 * Regression coverage for issue #89: every concrete producer previously overrode {@code
 * process(Exchange)} directly, so none of them ever consulted {@code exceptionHandler} - a
 * producer's own failure always propagated uncaught, bypassing retry/dead-letter channels
 * entirely, unlike a processor step's failure. {@code process(Exchange)} is now {@code final} on
 * {@link DefaultProducer}, wrapping the new {@link DefaultProducer#doProcess(ExchangeImpl)} hook in
 * the same try/catch shape {@code AbstractProcessorNode} already had.
 */
public class DefaultProducerTest {

    private static final class FailingProducer extends DefaultProducer {
        private final RuntimeException failure;

        FailingProducer(Endpoint endpoint, RuntimeException failure) {
            super(endpoint);
            this.failure = failure;
        }

        @Override
        public void doProcess(ExchangeImpl exchange) {
            throw failure;
        }
    }

    private static final class CapturingExceptionHandler implements ExceptionHandler {
        private Throwable capturedException;
        private ExchangeImpl capturedExchange;

        @Override
        public void handleException(Throwable exception, Exchange ioContext) {
            this.capturedException = exception;
            this.capturedExchange = (ExchangeImpl) ioContext;
        }
    }

    private static FailingProducer newFailingProducer(RuntimeException failure) {
        final Endpoint endpoint = new DefaultEndpoint(EndpointURL.parse("out-endpoint"));
        return new FailingProducer(endpoint, failure);
    }

    private static ExchangeImpl newExchange() {
        return new ExchangeImpl(new SimpleMessage("test-id"));
    }

    @Test
    public void givenNoExceptionHandler_whenDoProcessThrows_thenTheExceptionPropagates() {
        final RuntimeException failure = new RuntimeException("simulated producer failure");
        final FailingProducer producer = newFailingProducer(failure);

        try {
            producer.process(newExchange());
            Assert.fail("expected the exception to propagate when no exceptionHandler is set");
        } catch (RuntimeException caught) {
            Assert.assertSame(failure, caught);
        }
    }

    @Test
    public void givenAnExceptionHandler_whenDoProcessThrows_thenTheHandlerIsInvokedInsteadOfPropagating() {
        final RuntimeException failure = new RuntimeException("simulated producer failure");
        final FailingProducer producer = newFailingProducer(failure);
        final CapturingExceptionHandler handler = new CapturingExceptionHandler();
        producer.setExceptionHandler(handler);
        producer.setProcessorName("out-endpoint");

        final ExchangeImpl exchange = newExchange();
        producer.process(exchange); // must not throw - the handler resolves it instead

        Assert.assertSame(failure, handler.capturedException);
        Assert.assertSame(exchange, handler.capturedExchange);
        Assert.assertEquals("out-endpoint", exchange.getProperty(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, String.class));
    }

}
