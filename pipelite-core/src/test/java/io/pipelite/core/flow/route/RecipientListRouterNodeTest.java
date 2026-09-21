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
package io.pipelite.core.flow.route;

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.dsl.route.RecipientList;
import io.pipelite.expression.ExpressionParser;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Issue #105: a failure while forwarding to a recipient is handed to the flow's exception handler,
 * like a failed processor step, instead of escaping the node.
 */
public class RecipientListRouterNodeTest {

    private PipeliteContext pipeliteContext;
    private ExchangeFactory exchangeFactory;
    private RecipientListRouterNode subject;

    @Before
    public void setup() {

        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
        pipeliteContext = Mockito.mock(PipeliteContext.class);
        Mockito.when(pipeliteContext.getExchangeFactory()).thenReturn(exchangeFactory);

        subject = new RecipientListRouterNode(
            RecipientList.of("link://first-in", "link://second-in"),
            new ExpressionConditionEvaluator(new ExpressionParser()));
        subject.setPipeliteContext(pipeliteContext);
    }

    @Test
    public void givenEveryRecipientCanBeDelivered_thenEachGetsACopy() {

        final ExchangeImpl exchange = exchangeFactory.createExchange("payload");
        subject.process(exchange);

        Mockito.verify(pipeliteContext).supplyExchange(Mockito.eq("link://first-in"), Mockito.any());
        Mockito.verify(pipeliteContext).supplyExchange(Mockito.eq("link://second-in"), Mockito.any());
    }

    @Test
    public void givenARecipientThatCannotBeDelivered_thenTheFailureIsHandedToTheExceptionHandler() {

        final IllegalArgumentException cause = new IllegalArgumentException("nobody there");
        Mockito.doThrow(cause).when(pipeliteContext).supplyExchange(Mockito.eq("link://second-in"), Mockito.any());
        final AtomicReference<Throwable> handled = new AtomicReference<>();
        subject.setProcessorName("to-recipient-list");
        subject.setExceptionHandler((exception, exchange) -> handled.set(exception));

        final ExchangeImpl exchange = exchangeFactory.createExchange("payload");
        subject.process(exchange);

        Assert.assertTrue(handled.get() instanceof IllegalStateException);
        Assert.assertTrue(handled.get().getMessage(), handled.get().getMessage().contains("link://second-in"));
        Assert.assertSame(cause, handled.get().getCause());
        Assert.assertEquals("to-recipient-list",
            exchange.getProperty(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, String.class));
        // Not atomic, on purpose: the first recipient was already served when the second failed
        Mockito.verify(pipeliteContext).supplyExchange(Mockito.eq("link://first-in"), Mockito.any());
    }

    @Test
    public void givenAFailureAndNoExceptionHandler_thenItIsRethrown() {

        Mockito.doThrow(new IllegalArgumentException("nobody there"))
            .when(pipeliteContext).supplyExchange(Mockito.eq("link://first-in"), Mockito.any());

        try {
            subject.process(exchangeFactory.createExchange("payload"));
            Assert.fail("expected the failure to be rethrown");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("link://first-in"));
        }
    }

}
