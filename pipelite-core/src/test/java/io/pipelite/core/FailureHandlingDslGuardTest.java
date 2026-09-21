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
package io.pipelite.core;

import io.pipelite.dsl.definition.builder.BuildOperations;
import io.pipelite.dsl.definition.builder.retry.RetryTerminalOperations;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Issue #91: the three failure-handling entry points are mutually exclusive by type in a fluent
 * chain, but the builders are mutable - these cover the runtime guards that close the gap when a
 * caller keeps a reference instead of chaining.
 */
public class FailureHandlingDslGuardTest {

    private static BuildOperations newBuilder() {
        return Pipelite.defineFlow("guard-flow")
            .fromSource("guard-in")
            .toSink("guard-out");
    }

    @Test
    public void givenARetryIsDeclared_whenAnExceptionHandlerIsDeclaredOnTheSameBuilder_thenItFailsFast() {
        final BuildOperations builder = newBuilder();
        builder.withRetry(retry -> retry.onErrorChannel(err -> err.toDLQ()));
        try {
            builder.withExceptionHandler((exception, ioContext) -> { });
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("mutually exclusive"));
        }
    }

    @Test
    public void givenAnErrorChannelIsDeclared_whenARetryIsDeclaredOnTheSameBuilder_thenItFailsFast() {
        final BuildOperations builder = newBuilder();
        builder.withErrorChannel(err -> err.toDLQ());
        try {
            builder.withRetry(retry -> retry.onErrorChannel(err -> err.toDLQ()));
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("mutually exclusive"));
        }
    }

    @Test
    public void givenAnExceptionHandlerIsDeclared_whenAnErrorChannelIsDeclaredOnTheSameBuilder_thenItFailsFast() {
        final BuildOperations builder = newBuilder();
        builder.withExceptionHandler((exception, ioContext) -> { });
        try {
            builder.withErrorChannel(err -> err.toDLQ());
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("mutually exclusive"));
        }
    }

    @Test
    public void givenARetryExhaustionActionIsDeclared_whenASecondOneIsDeclared_thenItFailsFast() {
        try {
            newBuilder().withRetry(retry -> {
                retry.onErrorChannel(err -> err.toDLQ());
                final RetryTerminalOperations terminal = retry.onExceptionHandler((exception, ioContext) -> { });
                return terminal;
            });
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("exactly one exhaustion action"));
        }
    }

    @Test
    public void givenAnErrorChannelTargetIsDeclared_whenASecondOneIsDeclared_thenItFailsFast() {
        try {
            newBuilder().withErrorChannel(err -> {
                err.toChannel("link://some-flow");
                return err.toDLQ();
            });
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("exactly one target"));
        }
    }

}
