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

import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.flow.expression.TextExpressionEvaluator;
import io.pipelite.dsl.route.*;
import io.pipelite.expression.ExpressionParser;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicReference;

public class RouterNodeTest {

    private PipeliteContext pipeliteContext;
    private ExchangeFactory exchangeFactory;
    private RouterNode subject;

    @Before
    public void setup(){

        pipeliteContext = Mockito.mock(PipeliteContext.class);
        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));

        final ExpressionParser expressionParser = new ExpressionParser();
        final ConditionEvaluator conditionEvaluator = new ExpressionConditionEvaluator(expressionParser);

        final RoutingTable<ExpressionCondition> routingTable = new RoutingTable<>(conditionEvaluator);
        routingTable.add(new RouteEntry<>(RecipientList.of("Exit-LasVegas"), new ExpressionCondition("Headers['Destination'] == 'LasVegas'")));
        routingTable.add(new RouteEntry<>(RecipientList.of("Exit-SanFrancisco"), new ExpressionCondition("Headers['Destination'] == 'SanFrancisco'")));
        routingTable.add(new RouteEntry<>(RecipientList.of("Exit-LosAngeles"), new ExpressionCondition("Headers['Destination'] == 'LosAngeles'")));
        routingTable.setDefaultRoutes("Exit-Airport");

        final TextExpressionEvaluator textExpressionEvaluator = new TextExpressionEvaluator(expressionParser);
        subject = new RouterNode(routingTable, textExpressionEvaluator);

        subject.setPipeliteContext(pipeliteContext);

    }

    @Test
    public void shouldSupplyExchangeCorrectly(){

        final ExchangeImpl exchange = exchangeFactory.createExchange();
        exchange.putHeader("Destination", "LosAngeles");
        subject.process(exchange);

        Mockito.verify(pipeliteContext).supplyExchange(Mockito.same("Exit-LosAngeles"), Mockito.any());
    }

    @Test
    public void shouldSupplyExchangeOnDefaultRoute(){
        final ExchangeImpl exchange = exchangeFactory.createExchange();
        exchange.putHeader("Destination", "Okinawa");
        subject.process(exchange);

        Mockito.verify(pipeliteContext).supplyExchange(Mockito.same("Exit-Airport"), Mockito.any());
    }

    /**
     * Issue #105: a failure while routing is handed to the flow's exception handler, like a failed
     * processor step, instead of escaping the node.
     */
    @Test
    public void givenADestinationThatCannotBeDelivered_thenTheFailureIsHandedToTheExceptionHandler(){

        final IllegalArgumentException cause = new IllegalArgumentException("nobody there");
        Mockito.doThrow(cause).when(pipeliteContext).supplyExchange(Mockito.anyString(), Mockito.any());
        final AtomicReference<Throwable> handled = new AtomicReference<>();
        subject.setProcessorName("to-route");
        subject.setExceptionHandler((exception, exchange) -> handled.set(exception));

        final ExchangeImpl exchange = exchangeFactory.createExchange();
        exchange.putHeader("Destination", "LosAngeles");
        subject.process(exchange);

        Assert.assertTrue(handled.get() instanceof IllegalStateException);
        Assert.assertTrue(handled.get().getMessage(), handled.get().getMessage().contains("Exit-LosAngeles"));
        Assert.assertSame(cause, handled.get().getCause());
        Assert.assertEquals("to-route", exchange.getProperty(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, String.class));
    }

    @Test
    public void givenNoRouteAndNoDefaultRoute_thenTheFailureIsHandedToTheExceptionHandler(){

        final RoutingTable<ExpressionCondition> withoutDefault = new RoutingTable<>(new ExpressionConditionEvaluator(new ExpressionParser()));
        withoutDefault.add(new RouteEntry<>(RecipientList.of("Exit-LasVegas"), new ExpressionCondition("Headers['Destination'] == 'LasVegas'")));
        final RouterNode router = new RouterNode(withoutDefault, new TextExpressionEvaluator(new ExpressionParser()));
        router.setPipeliteContext(pipeliteContext);
        router.setProcessorName("to-route");
        final AtomicReference<Throwable> handled = new AtomicReference<>();
        router.setExceptionHandler((exception, exchange) -> handled.set(exception));

        final ExchangeImpl exchange = exchangeFactory.createExchange();
        exchange.putHeader("Destination", "Okinawa");
        router.process(exchange);

        Assert.assertTrue(handled.get().getMessage(), handled.get().getMessage().contains("unresolved route name"));
        Mockito.verifyNoInteractions(pipeliteContext);
    }

    @Test
    public void givenAFailureAndNoExceptionHandler_thenItIsRethrownUnchanged(){

        Mockito.doThrow(new IllegalArgumentException("nobody there")).when(pipeliteContext).supplyExchange(Mockito.anyString(), Mockito.any());

        final ExchangeImpl exchange = exchangeFactory.createExchange();
        exchange.putHeader("Destination", "LosAngeles");
        try {
            subject.process(exchange);
            Assert.fail("expected the failure to be rethrown");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("Exit-LosAngeles"));
        }
    }

}
