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

import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.process.Processor;
import io.pipelite.test.support.matchers.Action;
import io.pipelite.test.support.matchers.Actions;
import io.pipelite.test.support.matchers.Expectation;
import io.pipelite.test.support.matchers.Expectations;
import io.pipelite.test.support.matchers.Precondition;
import io.pipelite.test.support.matchers.Preconditions;
import io.pipelite.test.support.matchers.StepExpectation;

import java.util.function.Consumer;

/**
 * Single-import entry point for Pipelite BDD tests.
 *
 * <p>Aggregates {@link PipeliteTestFixture#given}, {@link Preconditions},
 * {@link Actions} and {@link Expectations} so that test classes only need:
 *
 * <pre>{@code
 * import static io.pipelite.test.PipeliteTest.*;
 * }</pre>
 *
 * <p>Example — processor mode:
 * <pre>{@code
 * given(
 *         header("Source-System", "Legacy-API"),
 *         inputPayload(Map.of("price", 100)))
 *     .when(process(myProcessor))
 *     .then(isSuccess());
 * }</pre>
 *
 * <p>Example — flow mode:
 * <pre>{@code
 * given(
 *         flowDefinition(flow),
 *         header("X-Order-Id", "ORD-001"),
 *         inputPayload(order))
 *     .when(supplyTo("orders-in"))
 *     .then(isExecutionCompleted())
 *     .inspectStep("enrich", hasHeader("X-Enriched-By"));
 * }</pre>
 */
public final class PipeliteTest {

    private PipeliteTest() {
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static WhenOperations given(Precondition... preconditions) {
        return PipeliteTestFixture.given(preconditions);
    }

    // -------------------------------------------------------------------------
    // Preconditions
    // -------------------------------------------------------------------------

    public static Precondition header(String name, Object value) {
        return Preconditions.header(name, value);
    }

    public static Precondition inputPayload(Object payload) {
        return Preconditions.inputPayload(payload);
    }

    public static Precondition flowDefinition(FlowDefinition flowDefinition) {
        return Preconditions.flowDefinition(flowDefinition);
    }

    public static Precondition flowConfiguration(Class<?> configClass) {
        return Preconditions.flowConfiguration(configClass);
    }

    public static Precondition flowConfiguration(Class<?> configClass, Object... dependencies) {
        return Preconditions.flowConfiguration(configClass, dependencies);
    }

    public static Precondition timeout(long seconds) {
        return Preconditions.timeout(seconds);
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    public static Action process(Processor processor) {
        return Actions.process(processor);
    }

    public static Action supplyTo(String endpointURL) {
        return Actions.supplyTo(endpointURL);
    }

    // -------------------------------------------------------------------------
    // Expectations
    // -------------------------------------------------------------------------

    public static Expectation isSuccess() {
        return Expectations.isSuccess();
    }

    public static Expectation isFailure() {
        return Expectations.isFailure();
    }

    public static Expectation isExecutionStopped() {
        return Expectations.isExecutionStopped();
    }

    public static Expectation isExecutionCompleted() {
        return Expectations.isExecutionCompleted();
    }

    public static Expectation isNotExecutionCompleted() {
        return Expectations.isNotExecutionCompleted();
    }

    public static Expectation hasHeader(String name) {
        return Expectations.hasHeader(name);
    }

    public static Expectation headerEquals(String name, Object expectedValue) {
        return Expectations.headerEquals(name, expectedValue);
    }

    public static Expectation noHeader(String name) {
        return Expectations.noHeader(name);
    }

    public static Expectation payloadEquals(Object expected) {
        return Expectations.payloadEquals(expected);
    }

    public static Expectation failureCause(Throwable expected) {
        return Expectations.failureCause(expected);
    }

    public static <T> Expectation payloadAs(Class<T> type, Consumer<T> assertions) {
        return Expectations.payloadAs(type, assertions);
    }

    /**
     * Groups one or more expectations that apply to the <em>final</em> exchange.
     * Use alongside {@link #step} inside a single {@code then(...)} call to
     * make the target of each assertion explicit.
     */
    public static Expectation output(Expectation... expectations) {
        return Expectations.output(expectations);
    }

    /**
     * Creates a step-level expectation that verifies {@code expectations}
     * against the exchange snapshot captured after the named step ran.
     * Pass it to {@code then(...)} alongside {@link #output} assertions.
     */
    public static StepExpectation step(String stepName, Expectation... expectations) {
        return Expectations.step(stepName, expectations);
    }
}
