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
package io.pipelite.dsl.headers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Which headers cross the border between a flow and the outside: the headers of a request or of a record that a source
 * puts on the exchange, and the headers of the exchange that a sink hands to the other system. A value, not code: it is
 * what the user declared, applied by the framework in one place for every channel adapter. Obtained from {@link
 * HeaderPropagationOperations}.
 * <p>
 * The default, when nothing is declared, is that no header crosses the border.
 */
public final class HeaderPropagation {

    private static final HeaderPropagation NONE = new HeaderPropagation(false, List.of());
    private static final HeaderPropagation ALL = new HeaderPropagation(true, List.of());

    private final boolean all;
    private final List<String> names;

    private HeaderPropagation(boolean all, List<String> names) {
        this.all = all;
        this.names = names;
    }

    /** What is declared when nothing is: no header crosses the border. */
    public static HeaderPropagation none() {
        return NONE;
    }

    /**
     * The propagation a configurator declares, {@code h -> h.filter("Idempotency-Key")}. Used by every construct that
     * propagates headers (a source, a sink); not meant to be called from user code.
     */
    public static HeaderPropagation from(HeaderPropagationConfigurator configurator) {
        Objects.requireNonNull(configurator, "headers is required and cannot be null");
        return Objects.requireNonNull(configurator.configure(new HeaderPropagationOperations() {
            @Override
            public HeaderPropagation filter(String... headerNames) {
                return ofNames(headerNames);
            }

            @Override
            public HeaderPropagation all() {
                return ALL;
            }
        }), "the propagation must be the one the HeaderPropagationOperations it is given returns");
    }

    /** For {@link HeaderPropagationOperations#filter}; not meant to be called from user code. */
    public static HeaderPropagation ofNames(String... headerNames) {
        if (headerNames == null || headerNames.length == 0) {
            throw new IllegalArgumentException("filter(...) needs at least one header name; use all() to propagate every header");
        }
        final List<String> names = new ArrayList<>();
        for (String headerName : headerNames) {
            if (headerName == null || headerName.isBlank()) {
                throw new IllegalArgumentException("a header name cannot be null or empty");
            }
            if (names.stream().noneMatch(headerName::equalsIgnoreCase)) {
                names.add(headerName);
            }
        }
        return new HeaderPropagation(false, Collections.unmodifiableList(names));
    }

    /** Whether every header crosses the border. */
    public boolean isAll() {
        return all;
    }

    /** Whether no header crosses the border. */
    public boolean isNone() {
        return !all && names.isEmpty();
    }

    /** The names of the headers that cross the border, empty when {@link #isAll()} or {@link #isNone()}. */
    public List<String> getNames() {
        return names;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof HeaderPropagation that && all == that.all && names.equals(that.names);
    }

    @Override
    public int hashCode() {
        return Objects.hash(all, names);
    }

    @Override
    public String toString() {
        return all ? "all headers" : names.isEmpty() ? "no headers" : "headers " + names;
    }
}
