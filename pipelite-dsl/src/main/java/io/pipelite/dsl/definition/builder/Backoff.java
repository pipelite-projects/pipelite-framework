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
 * Accepted by {@link RetryOperations#backoff(Backoff)} (issue #91) but not yet honored by the
 * retry-channel's own polling mechanics - a separate follow-up issue. A value type instead of a
 * bare {@link Duration} so the shape doesn't need to change again once the growth behavior is
 * actually implemented.
 */
public final class Backoff {

    public enum GrowthFunction {
        LINEAR, EXPONENTIAL
    }

    private final Duration initialDelay;
    private final GrowthFunction growthFunction;

    private Backoff(Duration initialDelay, GrowthFunction growthFunction) {
        this.initialDelay = Objects.requireNonNull(initialDelay, "initialDelay is required and cannot be null");
        this.growthFunction = Objects.requireNonNull(growthFunction, "growthFunction is required and cannot be null");
    }

    public static Backoff linear(Duration initialDelay) {
        return new Backoff(initialDelay, GrowthFunction.LINEAR);
    }

    public static Backoff exponential(Duration initialDelay) {
        return new Backoff(initialDelay, GrowthFunction.EXPONENTIAL);
    }

    public Duration getInitialDelay() {
        return initialDelay;
    }

    public GrowthFunction getGrowthFunction() {
        return growthFunction;
    }

}
