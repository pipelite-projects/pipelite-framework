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

import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.definition.SinkConfigurer;
import io.pipelite.dsl.headers.HeaderPropagation;
import io.pipelite.dsl.headers.PropagatesHeaders;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A test channel adapter, registered as {@code headers-sink://} in {@code META-INF/pipelite.factories}, whose sink has
 * headers: its configurer has the capability of choosing them, and its producer records the headers it is given, for {@code
 * PipeliteHeaderPropagationIntegrationTest}.
 */
public class HeadersSinkChannelAdapter implements ChannelAdapter {

    /** What a configurer of this adapter is: a sink with the capability of propagating headers, and nothing else. */
    public static final class HeadersSinkConfigurer implements SinkConfigurer, PropagatesHeaders {

        private HeaderPropagation propagation = HeaderPropagation.none();

        @Override
        public HeaderPropagation headerPropagation() {
            return propagation;
        }

        @Override
        public void setHeaderPropagation(HeaderPropagation propagation) {
            this.propagation = propagation;
        }
    }

    static final List<Map<String, String>> RECEIVED = new CopyOnWriteArrayList<>();

    static void reset() {
        RECEIVED.clear();
    }

    @Override
    public SinkConfigurer newSinkConfigurer() {
        return new HeadersSinkConfigurer();
    }

    @Override
    public Endpoint createEndpoint(String url) {
        final EndpointURL endpointURL = EndpointURL.parse(url);
        return new Endpoint() {
            @Override
            public EndpointURL getEndpointURL() {
                return endpointURL;
            }

            @Override
            public Producer createProducer() {
                return new Producer() {
                    @Override
                    public void produce(Exchange exchange) {
                        throw new AssertionError("the framework calls produce(exchange, headers)");
                    }

                    @Override
                    public void produce(Exchange exchange, Map<String, String> headers) {
                        RECEIVED.add(Map.copyOf(headers));
                    }
                };
            }
        };
    }
}
