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
package io.pipelite.spi.flow.exchange;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

public class SimpleMessage implements Message, Serializable {

    private final String id;
    private String correlationId;
    private Object payload;

    public SimpleMessage(String id) {
        Objects.requireNonNull(id, "id is required and cannot be null");
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getCorrelationId() {
        return this.correlationId;
    }

    @Override
    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    @Override
    public Object getPayload() {
        return payload;
    }

    @Override
    public void setPayload(Object payload) {
        //Objects.requireNonNull(payload, "payload is required and cannot be null");
        this.payload = payload;
    }

    @Override
    public Class<?> getPayloadType() {
        return payload.getClass();
    }

    @Override
    public boolean hasPayload() {
        return payload != null;
    }

    @Override
    public <T> Optional<T> tryGetPayloadAs(Class<T> expcetedType) {
        if (payload != null) {
            return Optional.of(getPayloadAs(expcetedType));
        }
        return Optional.empty();
    }

    @Override
    public <T> T getPayloadAs(Class<T> expcetedType) {
        if (payload == null) {
            return null;
        }
        if (expcetedType.isAssignableFrom(payload.getClass())) {
            return expcetedType.cast(payload);
        }
        throw new ClassCastException(String.format("Unable to cast from %s to %s",
            payload.getClass(), expcetedType));
    }
}
