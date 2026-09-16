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

import io.pipelite.spi.flow.AbstractFlowNode;

import java.util.Objects;

/**
 * Sealed per the pre-v1.0.0 audit (issue #79, Tier 4 #13): confirmed the only direct extenders in
 * the entire reactor are {@link DefaultConsumer} and {@link DefaultPollingConsumer} - both
 * declared {@code non-sealed}, since real adapters extend them freely (that's the actual, intended
 * extension point). Unlike {@code ExchangeFactory}/{@code MessageFactory}/{@code IdentityGenerator}
 * (also flagged in #13), sealing this base is genuinely free: no test double or cross-module
 * implementation of {@code AbstractConsumer} itself exists anywhere that a {@code permits} clause
 * would have to name.
 */
public abstract sealed class AbstractConsumer extends AbstractFlowNode implements Consumer
    permits DefaultConsumer, DefaultPollingConsumer {

    private final Endpoint endpoint;

    public AbstractConsumer(Endpoint endpoint){
        Objects.requireNonNull(endpoint, "endpoint is required and cannot be null");
        this.endpoint = endpoint;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }
}
