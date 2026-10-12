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
package io.pipelite.components.time;

import io.pipelite.dsl.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import io.pipelite.spi.source.SourceContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;

/**
 * What the time source does when it is polled, kept from the tests of the consumer it replaced
 * (issue #159): every poll is a tick whose payload is the current instant, and a poll is never
 * {@code null}, because the framework's {@code batchSize} loop stops at the first {@code null}.
 */
public class TimeSourceTest {

    private TimeSource subject;

    @Before
    public void setup() {
        subject = new TimeSource();
        subject.start(new SourceContext() {
            @Override
            public String flowName() {
                return "tick-flow";
            }

            @Override
            public Exchange newExchange(Object payload) {
                final ExchangeImpl exchange = new ExchangeImpl(new SimpleMessage(UUID.randomUUID().toString()));
                exchange.setInputPayload(payload);
                return exchange;
            }

            @Override
            public void accept(Exchange exchange) {
                throw new UnsupportedOperationException("a time source never hands an exchange over by itself");
            }

            @Override
            public ThreadFactory threadFactory() {
                return Thread::new;
            }

            @Override
            public boolean isRunning() {
                return true;
            }
        });
    }

    @Test
    public void givenAPoll_thenTheResultIsATickWhosePayloadIsTheCurrentInstant() {
        final LocalDateTime before = LocalDateTime.now();

        final Exchange exchange = subject.poll();

        final LocalDateTime after = LocalDateTime.now();
        Assert.assertNotNull(exchange);
        final Object payload = exchange.getInputPayload();
        Assert.assertTrue(payload instanceof LocalDateTime);
        Assert.assertFalse(((LocalDateTime) payload).isBefore(before));
        Assert.assertFalse(((LocalDateTime) payload).isAfter(after));
    }

    @Test
    public void aPollIsNeverNull() {
        for (int i = 0; i < 5; i++) {
            Assert.assertNotNull(subject.poll());
        }
    }
}
