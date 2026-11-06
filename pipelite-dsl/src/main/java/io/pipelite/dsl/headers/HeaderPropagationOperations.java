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

/** The ways to choose which headers cross the border between a flow and the outside: {@code h -> h.filter("Idempotency-Key")}. */
public interface HeaderPropagationOperations {

    /**
     * Only the headers with these names cross the border. The comparison ignores the case, as the headers of the
     * protocols that have them (HTTP) are compared, and the header has on the other side the name written here.
     */
    HeaderPropagation filter(String... headerNames);

    /**
     * Every header crosses the border. Headers are written to disk with the exchange (inbox, dumps, dead letter
     * queue) and sent to the other system: a request carries credentials ({@code Authorization}, cookies) that
     * should not end up there unless the flow needs them, so prefer {@link #filter} with the names it needs.
     */
    HeaderPropagation all();
}
