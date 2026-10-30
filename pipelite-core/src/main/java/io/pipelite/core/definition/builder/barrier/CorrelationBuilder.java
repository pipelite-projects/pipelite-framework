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

/** Builds the {@link Correlation} a user declares with {@code c -> c.headerName(...)} or {@code c.expression(...)}. */
final class CorrelationBuilder implements CorrelationOperations {

    @Override
    public Correlation headerName(String headerName) {
        return Correlation.ofHeader(headerName);
    }

    @Override
    public Correlation expression(String expression) {
        return Correlation.ofExpression(expression);
    }
}
