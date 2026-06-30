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
 *
 * <p>If multiple registered flows contain a step with the same name, the
 * fixture throws an {@link AssertionError} explaining the ambiguity. Implement
 * {@link #flowName()} (or use
 * {@link Expectations#step(String, String, Expectation...)}) to pin the
 * expectation to a specific flow and resolve the ambiguity.
 *
 * <h3>Why this is not {@code @FunctionalInterface}</h3>
 * <p>{@link Expectation} is {@code @FunctionalInterface} and can be written as
 * a lambda. {@code StepExpectation} cannot be, because it adds a second
 * abstract method ({@link #stepName()}) that makes the interface non-functional.
 * To create a custom step expectation, use an anonymous class or, preferably,
 * delegate to {@link Expectations#step(String, Expectation...)} and pass a
 * lambda as the inner {@code Expectation}.
 *
 * <h3>Direct use as {@code Expectation}</h3>
 * <p>Because {@code StepExpectation extends Expectation}, a {@code StepExpectation}
 * instance can be passed anywhere an {@code Expectation} is expected (e.g. inside
 * {@link Expectations#output(Expectation...)}). In that context {@link #stepName()}
 * and {@link #flowName()} are ignored — the expectation is verified against
 * whichever exchange the caller supplies. This is intentional: the step-routing
 * logic lives in {@code then(...)}, not in the expectation itself.
 */
public interface StepExpectation extends Expectation {

    String stepName();

    /**
     * Optional flow name for disambiguation. When {@code null} (the default),
     * {@code then(...)} resolves the snapshot by step name alone and throws
     * if the name matches steps in more than one registered flow.
     */
    default String flowName() {
        return null;
    }
}
