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

import io.pipelite.dsl.Headers;
import io.pipelite.spi.endpoint.DefaultEndpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Issue #115: an exchange put on {@code queue} - the only way that happens is durable-inbox
 * recovery, or the fallback of a retry that cannot find its failed processor (both plain {@code
 * consume(...)} calls) - used to sit there forever, since {@code receive(long)} never looked at it
 * and produced a fresh tick unconditionally on every call.
 */
public class TimePollingConsumerTest {

    private final ExchangeFactory exchangeFactory = new TestExchangeFactory();

    private TimePollingConsumer subject;

    @Before
    public void setup() {
        subject = new TimePollingConsumer(new DefaultEndpoint(EndpointURL.parse("tick")));
        subject.setExchangeFactory(exchangeFactory);
    }

    @Test
    public void givenNothingQueued_thenReceiveProducesATick() {
        final ExchangeImpl exchange = subject.receive();
        Assert.assertNotNull(exchange);
        Assert.assertTrue(exchange.getInputPayload() instanceof LocalDateTime);
    }

    @Test
    public void givenAnExchangeWasQueued_thenReceiveReturnsItInsteadOfATick() {
        final ExchangeImpl queued = exchangeFactory.createExchange("recovered-payload");
        subject.consume(queued);

        final ExchangeImpl received = subject.receive();

        Assert.assertSame(queued, received);
        Assert.assertEquals("recovered-payload", received.getInputPayload());
    }

    @Test
    public void givenTheQueueIsDrained_thenReceiveGoesBackToProducingTicks() {
        subject.consume(exchangeFactory.createExchange("recovered-payload"));
        subject.receive(); // drains the one queued exchange

        final ExchangeImpl exchange = subject.receive();

        Assert.assertTrue(exchange.getInputPayload() instanceof LocalDateTime);
    }

    @Test
    public void receiveNeverReturnsNull() {
        // ScheduledPollingConsumerService relies on this: a null would stop a batchSize>1 poll
        // loop early. Draining a queued exchange may legitimately run out, but a plain tick must
        // never itself be null.
        Assert.assertNotNull(subject.receive());
        Assert.assertNotNull(subject.receive(0));
    }

    private static class TestExchangeFactory implements ExchangeFactory {

        @Override
        public ExchangeImpl createExchange() {
            return createExchange(null, null);
        }

        @Override
        public ExchangeImpl createExchange(Headers headers) {
            return createExchange(headers, null);
        }

        @Override
        public ExchangeImpl createExchange(Object inputPayload) {
            return createExchange(null, inputPayload);
        }

        @Override
        public ExchangeImpl createExchange(Headers headers, Object inputPayload) {
            final ExchangeImpl exchange = new ExchangeImpl(new SimpleMessage(UUID.randomUUID().toString()), headers);
            if (inputPayload != null) {
                exchange.setInputPayload(inputPayload);
            }
            return exchange;
        }

        @Override
        public ExchangeImpl copyExchange(ExchangeImpl exchange) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ExchangeImpl nextExchange(ExchangeImpl current) {
            throw new UnsupportedOperationException();
        }
    }

}
