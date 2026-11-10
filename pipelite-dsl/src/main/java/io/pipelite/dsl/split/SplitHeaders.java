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
package io.pipelite.dsl.split;

/**
 * The headers that {@code split(...)} puts on each element it sends on (issue #149), so that a step further on can tell
 * which split the element comes from, where it was, and how many there were: to route on them, to log them, or to
 * correlate an {@code aggregate} by them. They are headers like any other: they cross to another system only if the
 * flow asks for it with {@code propagateHeaders(...)}.
 */
public final class SplitHeaders {

    /** The id of the split, a {@code String}: the same for every element of one run of the split. */
    public static final String ID = "Pipelite-Split-Id";

    /** The position of the element in the collection, an {@code Integer} counted from zero. */
    public static final String INDEX = "Pipelite-Split-Index";

    /** The number of elements of the split, an {@code Integer}. */
    public static final String SIZE = "Pipelite-Split-Size";

    private SplitHeaders() {
    }
}
