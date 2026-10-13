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

/** How the key of a barrier is read from an exchange. Built with {@link CorrelationOperations}. */
public interface Correlation {

    /** The key of the barrier {@code exchange} waits in. Never {@code null} or empty. */
    String keyOf(Exchange exchange);
}
