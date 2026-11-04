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
package io.pipelite.core.consumer.internal;

import io.pipelite.core.consumer.internal.EventDrivenConsumer.PriorityExchange;
import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.exchange.internal.DistributedIdentityGeneratorImpl;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.dsl.correlation.Correlation;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/** Issue #156: what keeps the exchanges of a key in sequence, without the flow around it. */
public class OrderedAdmissionTest {

    private ExchangeFactory exchangeFactory;
    private OrderedAdmission admission;
    private long ticket;

    @Before
    public void setup() {
        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
        admission = new OrderedAdmission(Correlation.ofHeader("customer-id"), "orders");
    }

    private PriorityExchange exchange(String customer) {
        final ExchangeImpl exchange = exchangeFactory.createExchange("payload");
        if (customer != null) {
            exchange.putHeader("customer-id", customer);
        }
        return PriorityExchange.withNormalPriority(exchange, ++ticket);
    }

    /** What the dispatcher does with an exchange it took: its key, and whether it may run. */
    private boolean admit(PriorityExchange item) {
        return admission.admit(admission.keyOf(item), item);
    }

    @Test
    public void givenAKeyWithNothingRunning_thenTheFirstExchangeRunsAtOnceAndTheSecondWaits() {
        Assert.assertTrue(admit(exchange("A")));
        Assert.assertFalse(admit(exchange("A")));
        Assert.assertEquals(1, admission.waitingCount());
    }

    @Test
    public void givenDifferentKeys_thenNoneWaitsForTheOthers() {
        Assert.assertTrue(admit(exchange("A")));
        Assert.assertTrue(admit(exchange("B")));
        Assert.assertTrue(admit(exchange("C")));
        Assert.assertEquals(0, admission.waitingCount());
    }

    @Test
    public void givenTheOwnerFinishes_thenTheOneThatWaitedTheLongestIsNextAndTheKeyNeverFreesBetweenThem() {
        final PriorityExchange first = exchange("A");
        final PriorityExchange second = exchange("A");
        final PriorityExchange third = exchange("A");
        Assert.assertTrue(admit(first));
        Assert.assertFalse(admit(second));
        Assert.assertFalse(admit(third));

        Assert.assertSame(second, admission.release("A"));
        Assert.assertFalse("a new arrival does not overtake the ones that waited", admit(exchange("A")));
        Assert.assertSame(third, admission.release("A"));
    }

    @Test
    public void givenNothingWaits_thenTheKeyIsFreeAndForgotten() {
        Assert.assertTrue(admit(exchange("A")));

        Assert.assertNull(admission.release("A"));

        Assert.assertTrue("free again", admit(exchange("A")));
        Assert.assertEquals(0, admission.waitingCount());
    }

    @Test
    public void givenAnExchangeWithoutTheKey_thenItHasNoKeyAndIsNotOrdered() {
        Assert.assertNull(admission.keyOf(exchange(null)));
    }

    @Test
    public void givenAKeyThatWasNeverAdmitted_thenReleasingItDoesNothing() {
        Assert.assertNull(admission.release("never-seen"));
    }
}
