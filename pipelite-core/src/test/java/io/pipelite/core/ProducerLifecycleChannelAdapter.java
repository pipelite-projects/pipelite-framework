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
package io.pipelite.core;

import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.endpoint.DefaultEndpoint;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A test channel adapter, registered as {@code producer-lifecycle://} in {@code
 * META-INF/pipelite.factories}, whose producers record when they are created, used and closed, for
 * {@code PipeliteProducerLifecycleIntegrationTest}.
 */
public class ProducerLifecycleChannelAdapter implements ChannelAdapter {

    static final AtomicInteger CREATED = new AtomicInteger();
    static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    static void reset() {
        CREATED.set(0);
        EVENTS.clear();
    }

    @Override
    public Endpoint createEndpoint(String url) {
        return new DefaultEndpoint(EndpointURL.parse(url), this) {
            @Override
            public Producer createProducer() {
                CREATED.incrementAndGet();
                return new Producer() {
                    @Override
                    public void produce(io.pipelite.dsl.Exchange exchange) {
                        EVENTS.add("produce");
                    }

                    @Override
                    public void close() {
                        EVENTS.add("close");
                    }
                };
            }
        };
    }
}
