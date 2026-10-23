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
package io.pipelite.core.flow.barrier;

import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.exchange.internal.SimpleMessage;
import io.pipelite.dsl.barrier.Correlation;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Issue #150: the key of a barrier is read from a header or from an expression, the same way on every exchange. */
public class CorrelationKeysTest {

    public static class Customer {
        private final String id;

        public Customer(String id) {
            this.id = id;
        }
    }

    public static class Order {
        private final String id;
        private final Customer customer;

        public Order(String id, Customer customer) {
            this.id = id;
            this.customer = customer;
        }
    }

    private static ExchangeImpl exchange(Object payload) {
        final ExchangeImpl exchange = new ExchangeImpl(new SimpleMessage("message-1"));
        exchange.setInputPayload(payload);
        return exchange;
    }

    private static void assertCannotBeCorrelated(Correlation correlation, ExchangeImpl exchange, String fragment) {
        try {
            CorrelationKeys.keyOf(correlation, exchange);
            Assert.fail("the exchange should not be correlated, saying: " + fragment);
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(fragment));
        }
    }

    @Test
    public void givenAHeader_thenTheKeyIsItsValue() {
        final ExchangeImpl exchange = exchange("payload");
        exchange.putHeader("order-id", "o-42");

        Assert.assertEquals("o-42", CorrelationKeys.keyOf(Correlation.ofHeader("order-id"), exchange));
    }

    @Test
    public void givenAMissingHeader_thenTheExchangeCannotBeCorrelatedAndTheMessageNamesIt() {
        assertCannotBeCorrelated(Correlation.ofHeader("order-id"), exchange("payload"), "order-id");
    }

    @Test
    public void givenAnExpressionOverTheHeaders_thenTheKeyIsItsResult() {
        final ExchangeImpl exchange = exchange("payload");
        exchange.putHeader("customer", "c-7");

        Assert.assertEquals("c-7", CorrelationKeys.keyOf(Correlation.ofExpression("Headers['customer']"), exchange));
    }

    @Test
    public void givenAnExpressionOverANestedPayload_thenItNavigatesTheProperties() {
        final ExchangeImpl exchange = exchange(new Order("o-1", new Customer("c-9")));

        Assert.assertEquals("o-1", CorrelationKeys.keyOf(Correlation.ofExpression("Payload.id"), exchange));
        Assert.assertEquals("c-9", CorrelationKeys.keyOf(Correlation.ofExpression("Payload.customer.id"), exchange));
    }

    @Test
    public void givenAnExpressionWithNoValueOnTheExchange_thenItCannotBeCorrelated() {
        assertCannotBeCorrelated(Correlation.ofExpression("Headers['absent']"), exchange("payload"), "Headers['absent']");
    }

    @Test
    public void givenAnExpressionThatDoesNotFitThePayload_thenItCannotBeCorrelatedInsteadOfFailingOddly() {
        assertCannotBeCorrelated(Correlation.ofExpression("Payload.customer.id"), exchange("a string"), "Payload.customer.id");
    }

    @Test
    public void givenNoPayload_thenAnExpressionOverTheHeadersStillWorks() {
        final ExchangeImpl exchange = exchange(null);
        exchange.putHeader("order-id", "o-5");

        Assert.assertEquals("o-5", CorrelationKeys.keyOf(Correlation.ofExpression("Headers['order-id']"), exchange));
    }

    @Test
    public void givenManyThreadsEvaluatingAtOnce_thenEachGetsTheKeyOfItsOwnExchange() throws Exception {
        final int threads = 16;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final List<String> wrong = Collections.synchronizedList(new ArrayList<>());
        try {
            for (int i = 0; i < threads; i++) {
                final String expected = "order-" + i;
                pool.submit(() -> {
                    final ExchangeImpl exchange = exchange(new Order(expected, new Customer("c")));
                    try {
                        start.await();
                        for (int n = 0; n < 200; n++) {
                            final String key = CorrelationKeys.keyOf(Correlation.ofExpression("Payload.id"), exchange);
                            if (!expected.equals(key)) {
                                wrong.add(expected + " got " + key);
                            }
                        }
                    } catch (Throwable t) {
                        wrong.add(expected + " failed: " + t);
                    }
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            Assert.assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        }

        Assert.assertTrue(wrong.toString(), wrong.isEmpty());
    }
}
