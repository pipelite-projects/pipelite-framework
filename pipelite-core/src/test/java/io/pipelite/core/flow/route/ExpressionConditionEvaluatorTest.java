package io.pipelite.core.flow.route;

import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.route.ExpressionCondition;
import io.pipelite.expression.ExpressionParser;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ExpressionConditionEvaluatorTest {

    private ExchangeFactory exchangeFactory;
    private ExpressionConditionEvaluator subject;

    @Before
    public void setup(){
        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
        subject = new ExpressionConditionEvaluator(new ExpressionParser());
    }

    @Test
    public void shouldEvaluatePayloadExpressionRouteCondition(){
        final IOContext ioContext = exchangeFactory.createExchange(new Person("Wayne", "Bruce"));
        final ExpressionCondition expressionRouteCondition = new ExpressionCondition("Payload.name == 'Bruce'");
        final boolean result = subject.evaluate(expressionRouteCondition, ioContext);
        Assert.assertTrue(result);
    }

    @Test
    public void shouldEvaluateHeadersExpressionRouteCondition(){
        final IOContext ioContext = exchangeFactory.createExchange();
        ioContext.putHeader("Batman-Opponent", "Joker");
        final ExpressionCondition expressionRouteCondition = new ExpressionCondition("Headers['Batman-Opponent'] == 'Joker'");
        final boolean result = subject.evaluate(expressionRouteCondition, ioContext);
        Assert.assertTrue(result);
    }

    public static class Person {
        private final String surname;
        private final String name;

        public Person(String surname, String name) {
            this.surname = surname;
            this.name = name;
        }

        public String getSurname() {
            return surname;
        }

        public String getName() {
            return name;
        }
    }
}
