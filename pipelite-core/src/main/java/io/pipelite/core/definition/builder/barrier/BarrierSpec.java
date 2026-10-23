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

import java.time.Duration;
import java.util.List;

/**
 * What a barrier was declared to do, validated and immutable: built by {@link BarrierSpecBuilder} when the flow
 * is defined, and read by the node that runs it and by the context when it starts.
 */
public final class BarrierSpec {

    /**
     * One flow the barrier waits for: the URL it was declared with, the name of the queue the flow reads, and
     * the key that correlates the waiting exchange with the execution of that flow (its own, or the shared one).
     */
    public record Await(String flowURL, String queueName, Correlation correlation) {
    }

    private final List<Await> awaits;
    private final Duration timeout;

    BarrierSpec(List<Await> awaits, Duration timeout) {
        this.awaits = List.copyOf(awaits);
        this.timeout = timeout;
    }

    /** The flows awaited, in the order they were declared. */
    public List<Await> getAwaits() {
        return awaits;
    }

    /** How long an exchange waits before it is given up. */
    public Duration getTimeout() {
        return timeout;
    }
}
