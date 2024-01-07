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
