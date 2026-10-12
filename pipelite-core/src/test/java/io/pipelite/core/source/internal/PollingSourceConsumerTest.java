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
package io.pipelite.core.source.internal;

import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.Headers;
import io.pipelite.spi.endpoint.DefaultEndpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import io.pipelite.spi.source.PollingSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * What the polling service gets when it asks for the next exchange (issue #159, kept from the tests of
 * the time consumer this replaces): what is already queued comes first, an entry recovered from the
 * durable inbox before anything new is polled, and only then the adapter's {@link PollingSource}.
 */
public class PollingSourceConsumerTest {

    private final ExchangeFactory exchangeFactory = new TestExchangeFactory();
    private final Deque<Object> payloadsToPoll = new ArrayDeque<>();
    private PollingSourceConsumer subject;

    @Before
    public void setup() {
        final PollingSource source = new PollingSource() {
            @Override
            public Exchange poll() {
                final Object payload = payloadsToPoll.poll();
                return payload == null ? null : exchangeFactory.createExchange(payload);
            }
        };
        subject = new PollingSourceConsumer(new DefaultEndpoint(EndpointURL.parse("tick")), source);
        subject.setExchangeFactory(exchangeFactory);
    }

    @Test
    public void givenNothingQueued_thenReceiveReturnsWhatTheSourceHasPolled() {
        payloadsToPoll.add("polled");

        final ExchangeImpl exchange = subject.receive();

        Assert.assertEquals("polled", exchange.getInputPayload());
    }

    @Test
    public void givenAnExchangeWasQueued_thenReceiveReturnsItBeforeAnythingPolled() {
        payloadsToPoll.add("polled");
        final ExchangeImpl queued = exchangeFactory.createExchange("recovered-payload");
        subject.consume(queued);

        final ExchangeImpl first = subject.receive();
        final ExchangeImpl second = subject.receive();

        Assert.assertSame(queued, first);
        Assert.assertEquals("polled", second.getInputPayload());
    }

    @Test
    public void givenTheSourceHasNothing_thenReceiveReturnsNull() {
        Assert.assertNull(subject.receive());
        Assert.assertNull(subject.receive(0));
    }

    @Test
    public void givenTheSourceHandsExchangesOverWithAcceptAndReturnsNull_thenReceiveReturnsThemInOrder() {
        final PollingSourceConsumer[] consumer = new PollingSourceConsumer[1];
        final int[] polls = {0};
        final PollingSource handingOver = new PollingSource() {
            @Override
            public Exchange poll() {
                if (polls[0]++ == 0) {
                    // what SourceContext#accept does: queue the exchange, durably
                    consumer[0].consume(exchangeFactory.createExchange("first"));
                    consumer[0].consume(exchangeFactory.createExchange("second"));
                }
                return null;
            }
        };
        consumer[0] = new PollingSourceConsumer(new DefaultEndpoint(EndpointURL.parse("tick")), handingOver);
        consumer[0].setExchangeFactory(exchangeFactory);

        final ExchangeImpl first = consumer[0].receive();
        final ExchangeImpl second = consumer[0].receive();
        final ExchangeImpl none = consumer[0].receive();

        Assert.assertEquals("what was handed over comes out of the same call that polled", "first", first.getInputPayload());
        Assert.assertEquals("second", second.getInputPayload());
        Assert.assertNull(none);
        Assert.assertEquals("the second call took the queue and did not poll again", 2, polls[0]);
    }

    @Test
    public void givenAnExchangeThatTheContextDidNotCreate_thenItIsRejected() {
        final Exchange foreign = new Exchange() {
            @Override public Headers getHeaders() { return null; }
            @Override public java.util.Optional<String> tryGetHeader(String headerName) { return java.util.Optional.empty(); }
            @Override public String expectHeader(String headerName) { return null; }
            @Override public <T> java.util.Optional<T> tryGetHeaderAs(String headerName, Class<T> expectedType) { return java.util.Optional.empty(); }
            @Override public void putHeader(String headerName, Object headerValue) { }
            @Override public void removeHeader(String headerName) { }
            @Override public boolean hasHeader(String headerName) { return false; }
            @Override public Class<?> getInputPayloadType() { return null; }
            @Override public Object getInputPayload() { return null; }
            @Override public <T> T getInputPayloadAs(Class<T> expectedType) { return null; }
            @Override public void setOutputPayload(Object payload) { }
        };
        final PollingSourceConsumer foreignSource = new PollingSourceConsumer(
            new DefaultEndpoint(EndpointURL.parse("tick")), new PollingSource() {
                @Override
                public Exchange poll() {
                    return foreign;
                }
            });

        try {
            foreignSource.receive();
            Assert.fail("an exchange not created by the SourceContext must be rejected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("SourceContext"));
        }
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
