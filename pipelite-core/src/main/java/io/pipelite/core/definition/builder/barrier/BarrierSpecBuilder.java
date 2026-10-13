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

import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.barrier.BarrierOperations;
import io.pipelite.dsl.barrier.Correlation;
import io.pipelite.dsl.barrier.CorrelationConfigurator;
import io.pipelite.dsl.barrier.Release;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The implementation of {@link BarrierOperations}: collects what the user declared and, in {@link #build()},
 * checks it, so a barrier that could never work is refused when the flow is defined.
 */
public final class BarrierSpecBuilder implements BarrierOperations {

    private Correlation correlation;
    private Set<String> contributors;
    private Integer contributions;
    private Function<Exchange, String> contributor;
    private Predicate<Exchange> failedWhen;
    private Release release = Release.all();
    private Duration timeout;

    @Override
    public BarrierOperations correlateBy(CorrelationConfigurator configurator) {
        Objects.requireNonNull(configurator, "correlation is required and cannot be null");
        this.correlation = Objects.requireNonNull(configurator.configure(new CorrelationBuilder()),
            "the correlation must be one the CorrelationOperations it is given returns");
        return this;
    }

    @Override
    public BarrierOperations expect(String... names) {
        if (names == null || names.length == 0) {
            throw new IllegalArgumentException("expect needs at least one contributor");
        }
        final Set<String> unique = new LinkedHashSet<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("a contributor name cannot be null or empty");
            }
            if (!unique.add(name)) {
                throw new IllegalArgumentException("the contributor '" + name + "' is declared twice");
            }
        }
        this.contributors = unique;
        return this;
    }

    @Override
    public BarrierOperations expect(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("expect needs at least one contribution, got " + count);
        }
        this.contributions = count;
        return this;
    }

    @Override
    public BarrierOperations contributorBy(Function<Exchange, String> contributor) {
        this.contributor = Objects.requireNonNull(contributor, "contributor is required and cannot be null");
        return this;
    }

    @Override
    public BarrierOperations failedWhen(Predicate<Exchange> failed) {
        this.failedWhen = Objects.requireNonNull(failed, "failed is required and cannot be null");
        return this;
    }

    @Override
    public BarrierOperations release(Release release) {
        this.release = Objects.requireNonNull(release, "release is required and cannot be null");
        return this;
    }

    @Override
    public BarrierOperations timeout(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout is required and cannot be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("the timeout of a barrier must be positive, got " + timeout);
        }
        this.timeout = timeout;
        return this;
    }

    /** Checks what was declared and returns it as an immutable {@link BarrierSpec}. */
    public BarrierSpec build() {
        if (correlation == null) {
            throw new IllegalArgumentException("a barrier needs correlateBy: what makes exchanges wait together");
        }
        if (contributors == null && contributions == null) {
            throw new IllegalArgumentException("a barrier needs expect: the contributors, or how many, that complete it");
        }
        if (contributors != null && contributions != null) {
            throw new IllegalArgumentException("a barrier expects either named contributors or a number of them, not both");
        }
        if (contributors != null && contributor == null) {
            throw new IllegalArgumentException("a barrier that expects named contributors needs contributorBy, "
                + "which says which contributor an exchange comes from");
        }
        if (timeout == null) {
            throw new IllegalArgumentException("a barrier needs a timeout: without one an order that never completes waits forever");
        }
        return new BarrierSpec(correlation, contributors, contributions == null ? 0 : contributions,
            contributor, failedWhen, release, timeout);
    }
}
