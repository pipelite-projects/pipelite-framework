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
package io.pipelite.test.support.matchers;

import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.test.TestProcessContribution;
import io.pipelite.test.ThenOperations;

/**
 * A single, composable assertion evaluated by {@link ThenOperations#then(Expectation...)}.
 *
 * <p>{@code contribution} is {@code null} when verifying a flow-mode exchange
 * (captured via {@code supplyTo} or a step snapshot), since contributions only
 * exist for isolated processor executions. Expectations that require a
 * contribution (e.g. {@link Expectations#isSuccess()}) throw
 * {@link AssertionError} with a clear message when invoked in flow mode.
 */
@FunctionalInterface
public interface Expectation {

    void verify(Exchange exchange, TestProcessContribution contribution);
}
