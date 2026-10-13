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
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Where the state of the barriers is kept. A public extension point of the framework, like the durable inbox,
 * for a backend of one's own; not something a channel adapter sees.
 * <p>
 * The one operation that matters is {@link #update}: it reads, changes and writes the state of a barrier
 * atomically for its key, which is what lets two contributions that arrive at once be counted, and the barrier
 * released, exactly once. Concerning durability, a state that {@code update} returned from has been written:
 * the arrival that is waiting is acknowledged to its inbox right after.
 */
public interface BarrierRepository {

    /**
     * Runs {@code action} on the state of {@code (barrierId, key)}, alone for that key, and keeps what it did to
     * the state. The state is created with {@code onAbsent} when there is none; if {@code onAbsent} returns
     * {@code null} nothing is created and nothing is run, and the result is {@code null}.
     */
    <R> R update(String barrierId, String key, Supplier<BarrierState> onAbsent, Function<BarrierState, R> action);

    Optional<BarrierState> tryLoad(String barrierId, String key);

    /** Every state whose {@link BarrierState#getExpiresAt()} is not after {@code now}, whatever its status. */
    List<BarrierState> findExpired(Instant now);

    void remove(String barrierId, String key);
}
