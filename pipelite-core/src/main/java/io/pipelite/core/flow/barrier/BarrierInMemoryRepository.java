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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/** Keeps the barriers in memory: a restart loses every correlation that was waiting. */
public class BarrierInMemoryRepository implements BarrierRepository {

    private record Id(String barrierId, String key) {
    }

    private final Map<Id, BarrierState> states = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings("unchecked")
    public <R> R update(String barrierId, String key, Supplier<BarrierState> onAbsent, Function<BarrierState, R> action) {
        final Object[] result = new Object[1];
        // compute runs alone for the key, which is the atomicity the contract asks for
        states.compute(new Id(barrierId, key), (id, current) -> {
            final BarrierState state = current != null ? current : onAbsent.get();
            if (state == null) {
                return null;
            }
            result[0] = action.apply(state);
            return state;
        });
        return (R) result[0];
    }

    @Override
    public Optional<BarrierState> tryLoad(String barrierId, String key) {
        return Optional.ofNullable(states.get(new Id(barrierId, key)));
    }

    @Override
    public List<BarrierState> findExpired(Instant now) {
        return states.values().stream()
            .filter(state -> !state.getExpiresAt().isAfter(now))
            .toList();
    }

    @Override
    public void remove(String barrierId, String key) {
        states.remove(new Id(barrierId, key));
    }
}
