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
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

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

        final Exchange exchange = exchangeFactory.createExchange();
        exchange.putHeader("Destination", "LosAngeles");
        subject.process(exchange);

        Mockito.verify(pipeliteContext).supplyExchange(Mockito.same("Exit-LosAngeles"), Mockito.any());
    }

    @Test
    public void shouldSupplyExchangeOnDefaultRoute(){
        final Exchange exchange = exchangeFactory.createExchange();
        exchange.putHeader("Destination", "Okinawa");
        subject.process(exchange);

        Mockito.verify(pipeliteContext).supplyExchange(Mockito.same("Exit-Airport"), Mockito.any());
    }

}
