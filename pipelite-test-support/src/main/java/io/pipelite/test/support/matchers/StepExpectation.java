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

/**
 * An {@link Expectation} that targets a named intermediate step snapshot
 * rather than the final exchange. Produced by {@link Expectations#step}.
 * When passed to {@link io.pipelite.test.ThenOperations#then}, the fixture
 * routes it to the step snapshot identified by {@link #stepName()} instead
 * of the final exchange.
 */
public interface StepExpectation extends Expectation {

    String stepName();
}
