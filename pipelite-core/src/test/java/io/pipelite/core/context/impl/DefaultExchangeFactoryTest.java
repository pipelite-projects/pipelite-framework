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
package io.pipelite.core.context.impl;

import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DefaultExchangeFactoryTest {

    private ExchangeFactory exchangeFactory;

    @Before
    public void setup() {
        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
    }

    @Test
    public void whenCopyExchange_thenHeadersInstanceIsNotShared() {
        final Exchange original = exchangeFactory.createExchange();
        original.putHeader("X-Trace-Id", "original-value");

        final Exchange copy = exchangeFactory.copyExchange(original);

        Assert.assertNotSame(original.getHeaders(), copy.getHeaders());
        Assert.assertEquals("original-value", copy.tryGetHeader("X-Trace-Id").orElse(null));
    }

    @Test
    public void whenCopyExchange_thenMutatingCopyHeadersDoesNotAffectOriginal() {
        final Exchange original = exchangeFactory.createExchange();
        original.putHeader("X-Trace-Id", "original-value");

        final Exchange copy = exchangeFactory.copyExchange(original);
        copy.putHeader("X-Trace-Id", "mutated-by-copy");

        Assert.assertEquals("original-value", original.tryGetHeader("X-Trace-Id").orElse(null));
    }

    @Test
    public void whenTwoCopyExchangeCallsOnSameParent_thenCopiesHaveIndependentHeaders() {
        final Exchange original = exchangeFactory.createExchange();
        original.putHeader("X-Trace-Id", "seed");

        final Exchange copyOne = exchangeFactory.copyExchange(original);
        final Exchange copyTwo = exchangeFactory.copyExchange(original);

        Assert.assertNotSame(copyOne.getHeaders(), copyTwo.getHeaders());

        copyOne.putHeader("X-Trace-Id", "mutated-by-copy-one");

        Assert.assertEquals("seed", copyTwo.tryGetHeader("X-Trace-Id").orElse(null));
        Assert.assertEquals("seed", original.tryGetHeader("X-Trace-Id").orElse(null));
    }

    @Test
    public void whenCopyExchange_thenOutputMessageInstanceIsNotShared() {
        final Exchange original = exchangeFactory.createExchange();
        original.setOutputPayload("original-output");

        final Exchange copy = exchangeFactory.copyExchange(original);

        Assert.assertNotSame(original.getOutput(), copy.getOutput());

        copy.setOutputPayload("mutated-by-copy");

        Assert.assertEquals("original-output", original.getOutput().getPayloadAs(String.class));
    }

    @Test
    public void whenNextExchange_thenHeaderSetBeforeCallIsPropagated() {
        final Exchange original = exchangeFactory.createExchange();
        original.putHeader("X-Trace-Id", "set-before");

        final Exchange next = exchangeFactory.nextExchange(original);

        Assert.assertEquals("set-before", next.tryGetHeader("X-Trace-Id").orElse(null));
    }

    @Test
    public void whenNextExchange_thenHeadersInstanceIsNotShared() {
        final Exchange original = exchangeFactory.createExchange();

        final Exchange next = exchangeFactory.nextExchange(original);

        Assert.assertNotSame(original.getHeaders(), next.getHeaders());
    }

    @Test
    public void whenHeaderSetOnOriginalAfterNextExchange_thenNotVisibleOnNext() {
        final Exchange original = exchangeFactory.createExchange();

        final Exchange next = exchangeFactory.nextExchange(original);
        original.putHeader("X-Late-Header", "added-after");

        Assert.assertFalse(next.hasHeader("X-Late-Header"));
    }

    @Test
    public void whenHeaderSetOnNextExchangeAfterCreation_thenNotVisibleOnOriginal() {
        final Exchange original = exchangeFactory.createExchange();

        final Exchange next = exchangeFactory.nextExchange(original);
        next.putHeader("X-Downstream-Header", "added-on-next");

        Assert.assertFalse(original.hasHeader("X-Downstream-Header"));
    }
}
