package io.pipelite.core.flow.process.filter;

import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.expression.ExpressionParser;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class ExpressionFilterNodeTest {

    private ExpressionParser expressionParser;
    private ExchangeFactory exchangeFactory;

    @Before
    public void setup(){
        expressionParser = new ExpressionParser();
        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
    }

    @Test
    public void givenPayload_whenFilter_thenStopExecution(){

        ExpressionFilterNode onlyDogsFilter = new ExpressionFilterNode("Headers['Animal-Kind'] eq 'Dog'", expressionParser);

        final Exchange exchange = exchangeFactory.createExchange();
        exchange.putHeader("Animal-Kind", "Cat");

        final ProcessContribution processContribution = Mockito.mock(ProcessContribution.class);
        onlyDogsFilter.process(exchange, processContribution);

        Mockito.verify(processContribution).stopExecution();

    }

    @Test
    public void givenPayload_whenFilter_thenForwardExecution(){

        ExpressionFilterNode onlyDogsFilter = new ExpressionFilterNode("Headers['Animal-Kind'] eq 'Dog'", expressionParser);

        final Exchange exchange = exchangeFactory.createExchange();
        exchange.putHeader("Animal-Kind", "Dog");

        final ProcessContribution processContribution = Mockito.mock(ProcessContribution.class);
        onlyDogsFilter.process(exchange, processContribution);

        Mockito.verifyNoInteractions(processContribution);

    }
}
