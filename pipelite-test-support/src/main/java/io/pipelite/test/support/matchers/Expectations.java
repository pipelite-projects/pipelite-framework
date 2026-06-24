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
import io.pipelite.spi.flow.exchange.Message;
import io.pipelite.test.ExecutionTarget;
import io.pipelite.test.TestProcessContribution;
import io.pipelite.test.ThenOperations;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Factory for the built-in {@link Expectation}s usable with
 * {@link ThenOperations#then(Expectation...)} and
 * {@link ThenOperations#inspectStep(String, Expectation...)}.
 */
public final class Expectations {

    private Expectations() {
    }

    /**
     * Asserts that the processor under test completed without throwing.
     * Only meaningful in processor mode (after {@link ExecutionTarget#process}).
     */
    public static Expectation isSuccess() {
        return (exchange, contribution) -> {
            requireContribution(contribution);
            if (!contribution.isSuccess()) {
                throw new AssertionError(String.format(
                    "Expected component to succeed, but failed with cause: %s", contribution.getFailureCause()));
            }
        };
    }

    /**
     * Asserts that the processor under test failed with an exception.
     * Only meaningful in processor mode (after {@link ExecutionTarget#process}).
     */
    public static Expectation isFailure() {
        return (exchange, contribution) -> {
            requireContribution(contribution);
            if (!contribution.isFailure()) {
                throw new AssertionError("Expected component to fail, but it completed successfully");
            }
        };
    }

    /**
     * Asserts that the processor under test invoked {@code stopExecution()}.
     * Only meaningful in processor mode (after {@link ExecutionTarget#process}).
     */
    public static Expectation isExecutionStopped() {
        return (exchange, contribution) -> {
            requireContribution(contribution);
            if (!contribution.isExecutionStopped()) {
                throw new AssertionError("Expected execution to be stopped, but it was not");
            }
        };
    }

    /**
     * Asserts that the flow under test reached its sink before the configured
     * timeout. Only meaningful in flow mode (after {@link ExecutionTarget#supplyTo(String)}).
     */
    public static Expectation isExecutionCompleted() {
        return (exchange, contribution) -> {
            if (exchange == null) {
                throw new AssertionError(
                    "Expected flow to complete, but it did not reach its sink before the configured timeout");
            }
        };
    }

    /**
     * Asserts that the flow under test did NOT reach its sink before the
     * configured timeout (e.g. it was filtered or stopped). Only meaningful
     * in flow mode (after {@link ExecutionTarget#supplyTo(String)}).
     */
    public static Expectation isNotExecutionCompleted() {
        return (exchange, contribution) -> {
            if (exchange != null) {
                throw new AssertionError("Expected flow to not complete, but it reached its sink");
            }
        };
    }

    /**
     * Asserts that the named header is present on the exchange under inspection.
     */
    public static Expectation hasHeader(String name) {
        return (exchange, contribution) -> {
            if (exchange == null || !exchange.hasHeader(name)) {
                throw new AssertionError(String.format("Expected header '%s' to be present, but it was not", name));
            }
        };
    }

    /**
     * Asserts that the named header is present on the exchange under
     * inspection and equal to {@code expectedValue}.
     */
    public static Expectation headerEquals(String name, Object expectedValue) {
        return (exchange, contribution) -> {
            if (exchange == null || !exchange.hasHeader(name)) {
                throw new AssertionError(String.format("Expected header '%s' to be present, but it was not", name));
            }
            final Object actual = expectedValue == null
                ? exchange.tryGetHeader(name).orElse(null)
                : exchange.tryGetHeaderAs(name, expectedValue.getClass()).orElse(null);
            if (!Objects.equals(expectedValue, actual)) {
                throw new AssertionError(String.format(
                    "Expected header '%s' to be <%s> but was <%s>", name, expectedValue, actual));
            }
        };
    }

    /**
     * Asserts that the named header is absent on the exchange under inspection.
     */
    public static Expectation noHeader(String name) {
        return (exchange, contribution) -> {
            if (exchange != null && exchange.hasHeader(name)) {
                throw new AssertionError(String.format("Expected header '%s' to be absent, but it was present", name));
            }
        };
    }

    /**
     * Asserts that the exchange's effective payload is equal to {@code expected}.
     */
    public static Expectation payloadEquals(Object expected) {
        return (exchange, contribution) -> {
            if (exchange == null) {
                throw new AssertionError("Expected an exchange to inspect, but none was available");
            }
            final Object actual = effectivePayload(exchange);
            if (!Objects.equals(expected, actual)) {
                throw new AssertionError(String.format("Expected payload <%s> but was <%s>", expected, actual));
            }
        };
    }

    /**
     * Asserts that the processor under test failed with exactly {@code expected}
     * as its failure cause. Only meaningful in processor mode (after
     * {@link ExecutionTarget#process}).
     */
    public static Expectation failureCauseEquals(Throwable expected) {
        return (exchange, contribution) -> {
            requireContribution(contribution);
            if (contribution.getFailureCause() != expected) {
                throw new AssertionError(String.format(
                    "Expected failure cause to be <%s> but was <%s>", expected, contribution.getFailureCause()));
            }
        };
    }

    /**
     * Asserts that the exchange's effective payload is of type {@code type}
     * and runs {@code assertions} against it, allowing callers to plug in
     * AssertJ/JUnit assertions on the typed payload for checks (e.g. asserting
     * on a single field of a larger payload) that a plain {@link #payloadEquals(Object)}
     * equality check can't express.
     */
    public static <T> Expectation payloadAs(Class<T> type, Consumer<T> assertions) {
        return (exchange, contribution) -> {
            if (exchange == null) {
                throw new AssertionError("Expected an exchange to inspect, but none was available");
            }
            final Object payload = effectivePayload(exchange);
            if (payload != null && !type.isAssignableFrom(payload.getClass())) {
                throw new AssertionError(String.format("Expected payload of type %s, but was %s",
                    type.getName(), payload.getClass().getName()));
            }
            assertions.accept(type.cast(payload));
        };
    }

    private static Object effectivePayload(Exchange exchange) {
        final Message outputMessage = exchange.getOutput();
        if (exchange.isOutputSet() && outputMessage != null && outputMessage.hasPayload()) {
            return outputMessage.getPayload();
        }
        return exchange.getInputPayload();
    }

    private static void requireContribution(TestProcessContribution contribution) {
        if (contribution == null) {
            throw new AssertionError(
                "This expectation requires a TestProcessContribution, which is only available in processor mode "
                    + "(after ExecutionTarget#process) — it cannot be used with supplyTo() or inspectStep()");
        }
    }
}
