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
package io.pipelite.spi.endpoint;

import io.pipelite.spi.source.Source;

/**
 * A channel's address, resolved: what a flow reads from ({@link #createSource()}) and writes to
 * ({@link #createProducer()}). A channel adapter creates one per URL in {@code ChannelAdapter#createEndpoint}.
 */
public interface Endpoint {

    EndpointURL getEndpointURL();

    default EndpointProperties getProperties() {
        return getEndpointURL().getProperties();
    }

    /**
     * What a flow reading from this endpoint ({@code fromSource}) uses. An endpoint that cannot be read
     * from, like a sink-only one, keeps this default.
     */
    default Source createSource() {
        throw new UnsupportedOperationException(getEndpointURL() + " cannot be used as a source");
    }

    /**
     * What a flow writing to this endpoint uses. An endpoint that cannot be written to, like a source-only
     * one, keeps this default.
     */
    default Producer createProducer() {
        throw new UnsupportedOperationException(getEndpointURL() + " cannot be used as a destination");
    }

}
