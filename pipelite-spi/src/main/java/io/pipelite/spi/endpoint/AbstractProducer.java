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
import io.pipelite.spi.flow.exchange.FlowNode;

import java.util.Objects;

public abstract class AbstractProducer extends AbstractFlowNode implements Producer {

    protected final Endpoint endpoint;

    public AbstractProducer(Endpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint is required and cannot be null");
        this.endpoint = endpoint;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    @Override
    public void setNext(FlowNode next) {
        throw new UnsupportedOperationException("Producer cannot have next flowNode");
    }

    @Override
    public boolean hasNext() {
        return false;
    }
}
