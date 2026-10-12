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
package io.pipelite.core.flow.internal;

import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.process.ExceptionHandler;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.endpoint.Producer;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import org.junit.Assert;
import org.junit.Test;

/**
 * Regression coverage for issue #89, kept when the old producer base class went away (issue #159):
 * every concrete producer previously overrode {@code process(Exchange)} directly, so none of them
 * ever consulted {@code exceptionHandler} - a producer's own failure always propagated uncaught,
 * bypassing retry/dead-letter channels entirely, unlike a processor step's failure. The node that
 * wraps a {@link Producer} now applies the flow's error handling around {@link Producer#produce}.
 */
public class ProducerNodeTest {

    private static final class CapturingExceptionHandler implements ExceptionHandler {
        private Throwable capturedException;
        private ExchangeImpl capturedExchange;

        @Override
        public void handleException(Throwable exception, Exchange ioContext) {
            this.capturedException = exception;
            this.capturedExchange = (ExchangeImpl) ioContext;
        }
    }

    private static ProducerNode newFailingNode(RuntimeException failure) {
        final Producer failing = exchange -> {
            throw failure;
        };
        return new ProducerNode(failing);
    }

    private static ExchangeImpl newExchange() {
        return new ExchangeImpl(new SimpleMessage("test-id"));
    }

    @Test
    public void givenNoExceptionHandler_whenProduceThrows_thenTheExceptionPropagates() {
        final RuntimeException failure = new RuntimeException("simulated producer failure");
        final ProducerNode node = newFailingNode(failure);

        try {
            node.process(newExchange());
            Assert.fail("expected the exception to propagate when no exceptionHandler is set");
        } catch (RuntimeException caught) {
            Assert.assertSame(failure, caught);
        }
    }

    @Test
    public void givenAnExceptionHandler_whenProduceThrows_thenTheHandlerIsInvokedInsteadOfPropagating() {
        final RuntimeException failure = new RuntimeException("simulated producer failure");
        final ProducerNode node = newFailingNode(failure);
        final CapturingExceptionHandler handler = new CapturingExceptionHandler();
        node.setExceptionHandler(handler);
        node.setProcessorName("out-endpoint");
        final ExchangeImpl exchange = newExchange();

        node.process(exchange); // must not throw - the handler resolves it instead

        Assert.assertSame(failure, handler.capturedException);
        Assert.assertSame(exchange, handler.capturedExchange);
        Assert.assertEquals("out-endpoint", exchange.getProperty(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, String.class));
    }

    @Test
    public void givenAProducerThatSucceeds_thenTheExchangeIsHandedToIt() {
        final ExchangeImpl[] received = new ExchangeImpl[1];
        final ProducerNode node = new ProducerNode(exchange -> received[0] = (ExchangeImpl) exchange);
        final ExchangeImpl exchange = newExchange();

        node.process(exchange);

        Assert.assertSame(exchange, received[0]);
    }

    @Test(expected = IllegalStateException.class)
    public void givenAProducerNode_thenItCannotHaveANextNode() {
        new ProducerNode(exchange -> { }).setNext(null);
    }
}
