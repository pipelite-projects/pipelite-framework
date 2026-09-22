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
package io.pipelite.core.context.internal;

/**
 * Names the framework reserves for its own internal flows (issue #116), defined once so {@code
 * DefaultPipeliteContext} (which creates the flow) and {@link
 * io.pipelite.core.context.internal.validation.ReservedFlowNameValidator} (which rejects a user
 * flow that collides with it) never drift apart.
 * <p>
 * {@code retry-channel} is the only one: the built-in retry channel {@code
 * DefaultPipeliteContext} creates under this name, the moment the first {@code withRetry(...)} flow
 * is met. It reads a {@code TypedSourceDefinition} of its own ({@code RetryEndpoint}), never a
 * {@code queue://}, so it was never truly reachable by this name - the collision #116 fixed was
 * with {@code flowRegistry}'s own bookkeeping, not with delivery. There is no equivalent for
 * {@code withErrorChannel(...)}/{@code toDLQ()}: a dead-lettered exchange is written straight to a
 * {@code DeadLetterQueueRepository} ({@code DeadLetterQueueExceptionHandler#handleException}), with
 * no flow, no consumer and no name of its own to collide with.
 */
public final class ReservedNames {

    public static final String RETRY_CHANNEL = "retry-channel";

    private ReservedNames() {
    }

}
