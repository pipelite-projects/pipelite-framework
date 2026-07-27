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
package io.pipelite.core.flow.split;

import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class UseOriginalAggregatorTest {

    private ExchangeFactory exchangeFactory;
    private AggregateRepository repository;
    private UseOriginalAggregator subject;

    @Before
    public void setup() {
        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
        repository = new AggregateInMemoryRepository();
        subject = new UseOriginalAggregator(repository);
    }

    @Test
    public void givenIdSavedInRepository_whenAggregate_thenReturnsSameExchangeReferenceWithResultsAsOutputPayload() {

        final Exchange original = exchangeFactory.createExchange(List.of("a", "b"));
        final String id = original.getInput().getId();
        repository.save(id, original);

        final Exchange aggregated = subject.aggregate(id, List.of("A", "B"));

        assertSame(original, aggregated);
        assertEquals(List.of("A", "B"), aggregated.getOutput().getPayloadAs(List.class));
    }

    @Test(expected = IllegalStateException.class)
    public void givenIdNeverSaved_whenAggregate_thenThrowsIllegalStateException() {
        subject.aggregate("never-saved-id", List.of());
    }
}
