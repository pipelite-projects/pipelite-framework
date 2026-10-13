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
package io.pipelite.core.definition.builder.barrier;

import io.pipelite.dsl.barrier.Correlation;
import io.pipelite.dsl.barrier.CorrelationOperations;

import java.util.Objects;
import java.util.function.Function;

/** Reads the key of a barrier from a header or from an attribute of the payload. */
final class CorrelationBuilder implements CorrelationOperations {

    @Override
    public Correlation headerName(String headerName) {
        if (headerName == null || headerName.isBlank()) {
            throw new IllegalArgumentException("headerName is required and cannot be null or empty");
        }
        return exchange -> require(exchange.tryGetHeader(headerName).orElse(null),
            "The exchange has no header '" + headerName + "' to correlate it by");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Correlation payloadAttribute(Function<T, ?> attribute) {
        Objects.requireNonNull(attribute, "attribute is required and cannot be null");
        return exchange -> {
            final Object payload = exchange.getInputPayload();
            final Object value;
            try {
                value = attribute.apply((T) payload);
            } catch (ClassCastException e) {
                throw new IllegalStateException("The payload of the exchange ("
                    + (payload == null ? "null" : payload.getClass().getName()) + ") is not the type the correlation reads", e);
            }
            return require(value == null ? null : String.valueOf(value),
                "The attribute of the payload to correlate the exchange by is null");
        };
    }

    private static String require(String key, String message) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(message);
        }
        return key;
    }
}
