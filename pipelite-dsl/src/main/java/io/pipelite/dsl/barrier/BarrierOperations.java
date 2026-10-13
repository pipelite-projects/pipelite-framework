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
package io.pipelite.dsl.barrier;

import io.pipelite.dsl.Exchange;

import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The settings of a barrier. {@link #correlateBy}, one of the two {@code expect} forms and {@link #timeout}
 * are required; the rest has a default. Mistakes are reported when the flow is defined, not when the first
 * exchange arrives.
 */
public interface BarrierOperations {

    /** What the exchanges that wait together have in common: the same key means the same barrier. */
    BarrierOperations correlateBy(CorrelationConfigurator correlation);

    /**
     * The barrier is complete when each of these contributors has arrived. Needs {@link #contributorBy},
     * which tells which contributor an exchange comes from.
     */
    BarrierOperations expect(String... contributors);

    /**
     * The barrier is complete when this many different contributors have arrived. Without {@link
     * #contributorBy} a contributor is a message: an exchange replayed after a crash counts once.
     */
    BarrierOperations expect(int contributions);

    /** The contributor an exchange comes from. Defaults to the id of the message. */
    BarrierOperations contributorBy(Function<Exchange, String> contributor);

    /**
     * A contribution that says the work failed. The barrier then does not wait for the others: the
     * correlation goes to the error handling of the flow. A contributor that fails inside its own flow never
     * arrives, and is covered by the {@link #timeout}.
     */
    BarrierOperations failedWhen(Predicate<Exchange> failed);

    /** What leaves the barrier once it is complete. Defaults to {@link Release#all()}. */
    BarrierOperations release(Release release);

    /** How long an incomplete barrier waits, counted from its first arrival, before it is given up. */
    BarrierOperations timeout(Duration timeout);
}
