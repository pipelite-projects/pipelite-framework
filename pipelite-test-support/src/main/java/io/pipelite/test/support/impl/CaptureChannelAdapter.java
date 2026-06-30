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

import java.lang.ref.WeakReference;
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
 * {@link CompletableFuture} keyed by a per-invocation UUID that the fixture
 * stores as an internal exchange property. Each {@code supplyTo()} invocation
 * generates a fresh UUID, so concurrent test executions are fully isolated
 * even when tests run in parallel within the same JVM.
 *
 * <h3>Memory safety</h3>
 * <p>{@code PENDING} stores {@link WeakReference}s to the futures rather than
 * strong references. {@link PipeliteTestFixture#supplyTo(String)} holds the
 * only strong reference to each future for the duration of the test call and
 * always calls {@link #deregister(String)} in its {@code finally} block to
 * remove the entry explicitly. If the JVM is abnormally terminated (e.g.
 * {@code System.exit()}, JVM crash) before {@code deregister} runs, the weak
 * references allow the futures (and the captured exchanges they may hold)
 * to be collected without being pinned by this map.
 *
 * <p>Registered automatically via {@code META-INF/pipelite.factories} whenever
 * {@code pipelite-test-support} is on the classpath.
 */
public class CaptureChannelAdapter implements ChannelAdapter {

    // Package-private: production flows must never reference this property key directly.
    // Use attachTestId(Exchange, String) from PipeliteTestFixture instead.
    static final String TEST_ID_PROPERTY = "io.pipelite.test.internal.capture.id";

    /**
     * The single {@code test://} endpoint every non-{@code link://} sink is
     * silently redirected to by {@link PipeliteTestFixture}. The resource
     * part is irrelevant to capture correctness — only {@link #TEST_ID_PROPERTY}
     * matters — so a fixed URL is enough.
     */
    public static final String CAPTURE_ENDPOINT_URL = "test://capture";

    private static final ConcurrentHashMap<String, WeakReference<CompletableFuture<Exchange>>> PENDING =
        new ConcurrentHashMap<>();

    public static CompletableFuture<Exchange> register(String testId) {
        final CompletableFuture<Exchange> future = new CompletableFuture<>();
        PENDING.put(testId, new WeakReference<>(future));
        return future;
    }

    public static void deregister(String testId) {
        PENDING.remove(testId);
    }

    /**
     * Stamps {@code testId} onto the exchange so that {@link CaptureProducer}
     * can route it to the correct pending future. Called by
     * {@link io.pipelite.test.PipeliteTestFixture} only — not part of the
     * public API and must not be called from production flow code.
     */
    public static void attachTestId(Exchange exchange, String testId) {
        exchange.setProperty(TEST_ID_PROPERTY, testId);
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
                final WeakReference<CompletableFuture<Exchange>> ref = PENDING.get(testId);
                if (ref != null) {
                    final CompletableFuture<Exchange> future = ref.get();
                    if (future != null) {
                        future.complete(exchange);
                    }
                }
            }
        }
    }
}
