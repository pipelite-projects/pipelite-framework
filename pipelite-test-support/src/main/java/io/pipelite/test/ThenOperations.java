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
package io.pipelite.test;

import io.pipelite.test.support.matchers.Action;
import io.pipelite.test.support.matchers.Expectation;

/**
 * Verifies and inspects the state of the exchange (or contribution) after
 * {@link WhenOperations#when(Action)} has run.
 */
public interface ThenOperations {

    /**
     * Evaluates one or more {@link Expectation}s against the final exchange
     * (and, in processor mode, the {@link TestProcessContribution}).
     *
     * @throws AssertionError if any expectation fails
     */
    ThenOperations then(Expectation... expectations);

    /**
     * Verifies expectations against the exchange snapshot captured right
     * after the named step executed during {@code Actions.supplyTo(...)}.
     *
     * @throws AssertionError if the named step was never reached, or if any
     *                        expectation fails
     */
    ThenOperations inspectStep(String stepName, Expectation... expectations);

    /**
     * Returns the output payload produced by the last executed step.
     * Falls back to the input payload when no output was explicitly set.
     *
     * @throws IllegalStateException if called before execution, or (in flow
     *                                mode) if the flow never reached the
     *                                {@code test://} sink
     */
    Object getOutputPayload();

    /**
     * Returns the output payload cast to {@code expectedType}.
     *
     * @throws IllegalStateException if called before execution, or (in flow
     *                                mode) if the flow never reached the
     *                                {@code test://} sink
     * @throws ClassCastException    if the payload cannot be cast to the type
     */
    <T> T getOutputPayloadAs(Class<T> expectedType);

    /**
     * Returns the value of the named header cast to {@code expectedType},
     * or {@code null} if absent. Kept beyond the literal spec (alongside
     * {@link #getOutputPayloadAs(Class)}) since several scenarios need to
     * assert an exact header value, not just its presence.
     *
     * @throws IllegalStateException if called before execution, or (in flow
     *                                mode) if the flow never reached the
     *                                {@code test://} sink
     */
    <T> T getHeaderAs(String name, Class<T> expectedType);
}
