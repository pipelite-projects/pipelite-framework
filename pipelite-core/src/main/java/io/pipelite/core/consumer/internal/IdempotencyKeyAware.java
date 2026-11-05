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
package io.pipelite.core.consumer.internal;

import io.pipelite.dsl.correlation.Correlation;

/**
 * Implemented by the consumers that fix the idempotency key of the exchanges that enter a flow (issue #40), and take
 * from the source how to read it from a request. Injected at flow-build time, like {@link DurableInboxAware}.
 */
public interface IdempotencyKeyAware {

    /** How the key is read from the exchanges the source creates; {@code null}, the default, generates it. */
    void setIdempotencyKey(Correlation key);
}
