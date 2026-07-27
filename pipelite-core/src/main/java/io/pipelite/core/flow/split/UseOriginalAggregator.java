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
package io.pipelite.core.flow.split;

import io.pipelite.spi.flow.exchange.Exchange;

import java.util.List;
import java.util.Objects;

/**
 * Only {@link Aggregator} implementation in this iteration: retrieves the original,
 * pre-split Exchange from the repository (by reference, never copied) and sets the
 * collected results as its output payload. Reusing the same Exchange instance is what
 * keeps identifier/headers/properties propagation to the next node identical to an
 * ordinary {@code .process(...)} step.
 */
public class UseOriginalAggregator implements Aggregator {

    private final AggregateRepository repository;

    public UseOriginalAggregator(AggregateRepository repository) {
        Objects.requireNonNull(repository, "repository is required and cannot be null");
        this.repository = repository;
    }

    @Override
    public Exchange aggregate(String id, List<Object> results) {
        final Exchange original = repository.tryLoad(id)
            .orElseThrow(() -> new IllegalStateException(
                String.format("No original Exchange found in AggregateRepository for id '%s'", id)));
        original.setOutputPayload(results);
        return original;
    }
}
