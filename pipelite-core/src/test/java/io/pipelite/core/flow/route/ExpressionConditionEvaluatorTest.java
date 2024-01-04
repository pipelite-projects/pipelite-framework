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
