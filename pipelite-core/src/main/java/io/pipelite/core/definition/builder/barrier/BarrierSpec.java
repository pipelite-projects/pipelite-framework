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

import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.barrier.Correlation;
import io.pipelite.dsl.barrier.Release;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * What a barrier was declared to do, validated and immutable: built by {@link BarrierSpecBuilder} when the
 * flow is defined and read by the node that runs it.
 */
public final class BarrierSpec {

    private final Correlation correlation;
    private final Set<String> expectedContributors;
    private final int expectedCount;
    private final Function<Exchange, String> contributor;
    private final Predicate<Exchange> failedWhen;
    private final Release release;
    private final Duration timeout;

    BarrierSpec(Correlation correlation, Set<String> expectedContributors, int expectedCount,
                Function<Exchange, String> contributor, Predicate<Exchange> failedWhen, Release release,
                Duration timeout) {
        this.correlation = correlation;
        this.expectedContributors = expectedContributors == null ? null : Set.copyOf(new LinkedHashSet<>(expectedContributors));
        this.expectedCount = expectedCount;
        this.contributor = contributor;
        this.failedWhen = failedWhen;
        this.release = release;
        this.timeout = timeout;
    }

    /** The key of the barrier {@code exchange} waits in. */
    public String keyOf(Exchange exchange) {
        return correlation.keyOf(exchange);
    }

    /** The contributor {@code exchange} comes from: the declared function, or the id of its message. */
    public String contributorOf(ExchangeImpl exchange) {
        if (contributor == null) {
            return exchange.getInput().getId();
        }
        final String name = contributor.apply(exchange);
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("The contributor of the exchange is null or empty: contributorBy must return a name");
        }
        return name;
    }

    /** Whether a contributor of this name is one the barrier waits for. Always true when it only counts. */
    public boolean expects(String contributorName) {
        return expectedContributors == null || expectedContributors.contains(contributorName);
    }

    /** Whether the contributors that arrived complete the barrier. */
    public boolean isCompletedBy(Set<String> arrived) {
        return expectedContributors != null
            ? arrived.containsAll(expectedContributors)
            : arrived.size() >= expectedCount;
    }

    /** Whether {@code exchange} is a contribution that says the work failed. Always false without {@code failedWhen}. */
    public boolean isFailure(Exchange exchange) {
        return failedWhen != null && failedWhen.test(exchange);
    }

    public Release getRelease() {
        return release;
    }

    public Duration getTimeout() {
        return timeout;
    }

    /** The names the barrier waits for, or {@code null} when it only counts. */
    public Set<String> getExpectedContributors() {
        return expectedContributors;
    }

    /** How many contributors complete the barrier when it only counts, 0 when it waits for names. */
    public int getExpectedCount() {
        return expectedCount;
    }
}
