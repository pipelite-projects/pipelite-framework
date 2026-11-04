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
package io.pipelite.core.flow.barrier;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where the state of the barriers is kept: the exchanges that wait, and the flows that have completed. A public
 * extension point of the framework, like the durable inbox, for a backend of one's own; not something a channel
 * adapter sees.
 * <p>
 * Every operation is atomic with respect to the others, which is what makes an exchange that arrives while the
 * flow it waits for completes released exactly once, never lost and never twice: either {@link #arrive} sees the
 * completion, or {@link #complete} sees the waiter. A state that an operation returned from is durable: the
 * arrival that is waiting is acknowledged to its inbox right after.
 */
public interface BarrierRepository {

    /**
     * Registers the wait of {@code waiter}, whose {@link Waiter#pending()} holds everything it waits for, and says
     * what the node does with it. The pairs that have already completed are taken off what it waits for; when no
     * pair is left, or one has failed, nothing is stored and the answer is {@code PROCEED} or {@code FAIL}. A
     * waiter that is already stored (an arrival replayed after a crash) is never stored twice.
     */
    WaitDisposition arrive(Waiter waiter);

    /**
     * Records that the flow {@code flow} completed for {@code key}, remembered until {@code retainUntil}, and
     * returns the waiters that were waiting only for it, which become {@code RELEASED}. The ones that wait for
     * more keep waiting, for one less. An outcome already recorded for the pair stays: nothing is returned the
     * second time.
     */
    List<Waiter> complete(String flow, String key, Instant retainUntil);

    /**
     * Records that the flow {@code flow} failed definitively for {@code key} and returns the waiters that were
     * waiting for it, which become {@code FAILED}: they do not wait for the others.
     */
    List<Waiter> fail(String flow, String key, Instant retainUntil);

    Optional<Waiter> tryLoad(String waiterId);

    /** The waiters whose wait is over ({@code RELEASED}, {@code FAILED} or {@code EXPIRED}) and that have not been dealt with yet. */
    List<Waiter> findResolved();

    /**
     * Gives up the waiters still waiting whose {@link Waiter#expiresAt()} is not after {@code now}: they become
     * {@code EXPIRED} and are returned. Atomic with the completions, so a waiter that a flow completing releases
     * is not also given up: it is one or the other, never both.
     */
    List<Waiter> expire(Instant now);

    /** The flow has dealt with the waiter, or it expired: it is forgotten. */
    void remove(String waiterId);

    /** Forgets the outcomes that are no longer needed. Returns how many. */
    int purgeCompletions(Instant now);
}
