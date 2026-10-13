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

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** What leaves a barrier once it is complete. */
public final class Release {

    public enum Kind {
        /** The first exchange that arrived goes on, unchanged. */
        FIRST,
        /** One exchange goes on, whose payload is the list of the payloads that arrived, in arrival order. */
        ALL,
        /** One exchange goes on, whose payload is what the function makes of the exchanges that arrived. */
        AGGREGATE
    }

    private static final Release FIRST = new Release(Kind.FIRST, null);
    private static final Release ALL = new Release(Kind.ALL, null);

    private final Kind kind;
    private final Function<List<Exchange>, Object> aggregator;

    private Release(Kind kind, Function<List<Exchange>, Object> aggregator) {
        this.kind = kind;
        this.aggregator = aggregator;
    }

    public static Release first() {
        return FIRST;
    }

    public static Release all() {
        return ALL;
    }

    /** The exchanges are handed to {@code aggregator} in arrival order; its result is the payload that goes on. */
    public static Release aggregate(Function<List<Exchange>, Object> aggregator) {
        return new Release(Kind.AGGREGATE, Objects.requireNonNull(aggregator, "aggregator is required and cannot be null"));
    }

    public Kind getKind() {
        return kind;
    }

    /** The function of {@link Kind#AGGREGATE}, {@code null} for the other kinds. */
    public Function<List<Exchange>, Object> getAggregator() {
        return aggregator;
    }
}
