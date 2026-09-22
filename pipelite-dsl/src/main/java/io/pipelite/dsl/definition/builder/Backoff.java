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
package io.pipelite.dsl.definition.builder;

import java.time.Duration;
import java.util.Objects;

/**
 * Accepted by {@link RetryOperations#backoff(Backoff)} (issue #91), honored by the retry
 * channel's own polling mechanics via {@link #delayBeforeAttempt(int)} (issue #95). A value type
 * instead of a bare {@link Duration} so the shape didn't need to change again once the growth
 * behavior was actually implemented.
 */
public final class Backoff {

    public enum BackoffStrategy {
        LINEAR, EXPONENTIAL
    }

    private final Duration initialDelay;
    private final BackoffStrategy strategy;

    private Backoff(Duration initialDelay, BackoffStrategy strategy) {
        this.initialDelay = Objects.requireNonNull(initialDelay, "initialDelay is required and cannot be null");
        this.strategy = Objects.requireNonNull(strategy, "strategy is required and cannot be null");
    }

    public static Backoff linear(Duration initialDelay) {
        return new Backoff(initialDelay, BackoffStrategy.LINEAR);
    }

    public static Backoff exponential(Duration initialDelay) {
        return new Backoff(initialDelay, BackoffStrategy.EXPONENTIAL);
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public BackoffStrategy getStrategy() {
        return strategy;
    }

    /**
     * The wait, counted from the previous attempt, before {@code attemptNumber} runs -
     * {@code attemptNumber} being the value a resubmitted {@code FlowExecutionDump} carries (2 for
     * the first retry, since attempt 1 is the original, un-retried send; never called with 1).
     * {@code LINEAR} grows by a constant step of {@code initialDelay} (2s, 4s, 6s, ... for an
     * initial delay of 2s); {@code EXPONENTIAL} doubles it every attempt (2s, 4s, 8s, ...). Both
     * are pinned to base 2/step 1 by design (issue #95): a configurable base, a maximum delay cap
     * and jitter were all deliberately left out of this first version.
     */
    public Duration delayBeforeAttempt(int attemptNumber) {
        if (attemptNumber < 2) {
            throw new IllegalArgumentException("attemptNumber must be at least 2, got " + attemptNumber);
        }
        final int retryOrdinal = attemptNumber - 1;
        return switch (strategy) {
            case LINEAR -> initialDelay.multipliedBy(retryOrdinal);
            case EXPONENTIAL -> initialDelay.multipliedBy(1L << (retryOrdinal - 1));
        };
    }

}
