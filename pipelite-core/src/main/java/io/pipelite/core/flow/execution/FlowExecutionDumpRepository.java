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
package io.pipelite.core.flow.execution;

import java.util.Optional;

public interface FlowExecutionDumpRepository {

    Optional<FlowExecutionDump> tryLoad(String id);

    /**
     * The oldest still-{@link FlowExecutionDumpStatus#PENDING} dump, if any — a dump {@link
     * #tryClaim(String)} has already claimed is not eligible and must not be returned here again,
     * regardless of how many times this is called while that claim stands. This is what lets a
     * batch-draining caller (e.g. {@code ScheduledPollingConsumerService}, see issue #61) advance
     * past a dump whose resume attempt is still in flight instead of being handed that exact same
     * dump on every iteration until it's removed.
     */
    Optional<FlowExecutionDump> poll();

    void save(FlowExecutionDump flowExecutionDump);
    void remove(String id);

    /**
     * Atomically transitions {@code id}'s dump from {@link FlowExecutionDumpStatus#PENDING} to
     * {@link FlowExecutionDumpStatus#IN_PROGRESS} and persists that change, returning {@code true}
     * only if this call performed the transition. Returns {@code false} without changing anything
     * if the dump is already {@code IN_PROGRESS} (someone else's claim already stands — including,
     * for a shared/durable repository, a different process instance) or no longer exists (already
     * resolved and removed). The state lives on the dump itself, not in a caller-side exclusion
     * list, so the claim is visible to every future {@link #poll()} call from any caller sharing
     * this repository, not just the one that made it.
     * <p>
     * Must be a single atomic operation from the caller's point of view — a naive {@code
     * tryLoad(id)} + check + {@code save(...)} sequence would let two concurrent callers both
     * observe {@code PENDING} and both "win" the claim.
     */
    boolean tryClaim(String id);

}
