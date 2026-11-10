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
package io.pipelite.dsl;

import java.util.Optional;

public interface Headers {

    /** The text of the header, whatever its type: a number or a boolean gives its {@code String.valueOf}. */
    Optional<String> tryGetHeader(String headerName);
    String expectHeader(String headerName);
    <T> Optional<T> tryGetHeaderAs(String headerName, Class<T> expectedType);
    /**
     * Puts a header. The value of a header is a scalar: a {@code String}, a {@code Boolean} or a number ({@code Byte},
     * {@code Short}, {@code Integer}, {@code Long}, {@code Float}, {@code Double}, {@code BigInteger}, {@code
     * BigDecimal}), which keeps its type. Headers are written to disk with the exchange, and the object of an
     * application there would tie what is stored to the version of its class. A {@code null} value removes the header.
     *
     * @throws IllegalArgumentException if the value is of another type; the message names the header and the type
     */
    void putHeader(String headerName, Object headerValue);
    void removeHeader(String headerName);
    boolean hasHeader(String headerName);

}
