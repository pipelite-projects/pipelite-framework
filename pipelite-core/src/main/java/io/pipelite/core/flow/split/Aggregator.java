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

/**
 * Pluggable aggregation algorithm, internal to the framework — never selected or configured
 * by the DSL caller. {@link SplitterNode} resolves a single {@link Aggregator} instance once,
 * lazily in {@code setPipeliteContext(...)}, since concrete implementations may need
 * collaborators (e.g. {@link AggregateRepository}) that don't exist yet at DSL-build time.
 */
public interface Aggregator {

    Exchange aggregate(String id, List<Object> results);
}
