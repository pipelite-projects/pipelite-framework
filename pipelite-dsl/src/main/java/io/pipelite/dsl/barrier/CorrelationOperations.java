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

/** The ways to read a key from an exchange. */
public interface CorrelationOperations {

    /** The key is the value of this header. */
    Correlation headerName(String headerName);

    /**
     * The key is the result of this expression, in the language of {@code filter}: {@code Payload} is the input
     * payload and {@code Headers['name']} a header, for instance {@code "Payload.order.id"}. The language has no
     * way to join text: to correlate by several values, set a header with the composed key in a previous step and
     * use {@link #headerName}.
     */
    Correlation expression(String expression);
}
