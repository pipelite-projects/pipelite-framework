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
package io.pipelite.test.support.impl;

import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.endpoint.DefaultEndpoint;
import io.pipelite.spi.endpoint.DefaultProducer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.test.PipeliteTestFixture;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Channel adapter for the {@code test://} protocol. {@link PipeliteTestFixture}
 * transparently redirects every flow's real terminal sink to this protocol
 * before registering it with the context, so this adapter captures the
 * arriving exchange and {@link PipeliteTestFixture} can inspect the final
 * state after {@link PipeliteTestFixture#supplyTo(String)} returns — without
 * the test author ever having to write {@code toSink("test://...")} themselves.
 *
 * <p>Because Pipelite's {@code EventDrivenConsumer} processes exchanges on a
 * dedicated background thread, the capture is coordinated through a
 * {@link CompletableFuture} keyed by a per-invocation test ID that the fixture
 * stores as an internal exchange property.
 *
 * <p>Registered automatically via {@code META-INF/pipelite.factories} whenever
 * {@code pipelite-test-support} is on the classpath.
 */
public class CaptureChannelAdapter implements ChannelAdapter {

    public static final String TEST_ID_PROPERTY = "__pipelite_test_id__";

    /**
     * The single {@code test://} endpoint every non-{@code link://} sink is
     * silently redirected to by {@link PipeliteTestFixture}. The resource
     * part is irrelevant to capture correctness — only {@link #TEST_ID_PROPERTY}
     * matters — so a fixed URL is enough.
     */
    public static final String CAPTURE_ENDPOINT_URL = "test://capture";

    private static final ConcurrentHashMap<String, CompletableFuture<Exchange>> PENDING =
        new ConcurrentHashMap<>();

    public static CompletableFuture<Exchange> register(String testId) {
        CompletableFuture<Exchange> future = new CompletableFuture<>();
        PENDING.put(testId, future);
        return future;
    }

    public static void deregister(String testId) {
        PENDING.remove(testId);
    }

    @Override
    public Endpoint createEndpoint(String url) {
        return new CaptureEndpoint(EndpointURL.parse(url));
    }

    private static class CaptureEndpoint extends DefaultEndpoint {

        CaptureEndpoint(EndpointURL endpointURL) {
            super(endpointURL);
        }

        @Override
        public Producer createProducer() {
            return new CaptureProducer(this);
        }
    }

    private static class CaptureProducer extends DefaultProducer {

        CaptureProducer(Endpoint endpoint) {
            super(endpoint);
        }

        @Override
        public void process(Exchange exchange) {
            final String testId = exchange.getProperty(TEST_ID_PROPERTY, String.class);
            if (testId != null) {
                final CompletableFuture<Exchange> future = PENDING.get(testId);
                if (future != null) {
                    future.complete(exchange);
                }
            }
        }
    }
}
