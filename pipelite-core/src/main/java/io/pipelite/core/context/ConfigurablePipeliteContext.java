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
package io.pipelite.core.context;

import io.pipelite.core.config.EndpointURLPropertyResolver;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.deadletter.DeadLetterQueueRepository;
import io.pipelite.core.flow.execution.inbox.DurableInboxDeadLetterWriter;
import io.pipelite.spi.inbox.DurableInboxProvider;

/**
 * Extension of {@link PipeliteContext} that exposes configuration hooks for
 * infrastructure customisation before {@link PipeliteContext#start()} is invoked.
 * Callers that need to plug in environment-specific behaviour (e.g. resolving
 * {@code ${key}} placeholders via the Spring {@code Environment}) should depend on
 * this interface rather than on the base {@code PipeliteContext}.
 */
public interface ConfigurablePipeliteContext extends PipeliteContext {

    /**
     * Overrides the strategy used to resolve {@code ${key}} placeholders in endpoint
     * URLs. Must be called before {@link PipeliteContext#start()}.
     */
    void setEndpointURLPropertyResolver(EndpointURLPropertyResolver resolver);

    /**
     * Sizes the shared, application-wide {@code fromSource} worker pool (see {@link
     * PipeliteContext#getSourceWorkerPool()}). Must be called before {@link
     * PipeliteContext#start()} — the pool is created once, at the start of {@code start()}.
     * If never called, a documented default size is used, so total thread count is bounded
     * even with zero configuration.
     */
    void setMaxSourceWorkerPoolSize(int size);

    /**
     * Overrides the retry-channel's dump repository — by default a file-backed one under {@code
     * PipeliteHome} (durable across a crash or restart, see issue #68). Pass a {@code
     * FlowExecutionDumpInMemoryRepository} instead to opt back into the old zero-I/O, crash-loses-
     * state behavior (e.g. for a short-lived test). Must be called before
     * {@link PipeliteContext#start()}, which is when the retry-channel and its dependents are
     * actually wired up.
     */
    void setFlowExecutionDumpRepository(FlowExecutionDumpRepository repository);

    /**
     * Overrides the durable-inbox provider used to protect a message from intake until it
     * reaches a terminal state (issue #70) — by default a local, segmented-log-backed provider
     * under {@code PipeliteHome}. Pass a provider backed by a shared store (e.g. Redis, issue
     * #72) for genuine multi-instance coordination; the local default only guards a single
     * process. Must be called before {@link PipeliteContext#start()}, which is when sources are
     * actually wired up.
     */
    void setDurableInboxProvider(DurableInboxProvider provider);

    /**
     * Overrides where a durable-inbox entry goes when its payload fails to deserialize during
     * recovery (issue #70) — most commonly a payload class whose shape changed between the run
     * that wrote the entry and the run trying to read it back. By default a local, file-based
     * writer under {@code PipeliteHome}, one file per dead-lettered entry. Must be called before
     * {@link PipeliteContext#start()}, which is when recovery actually runs.
     */
    void setDurableInboxDeadLetterWriter(DurableInboxDeadLetterWriter writer);

    /**
     * Overrides the framework's built-in, ready-to-use dead letter queue (issue #93) - by
     * default a file-backed one under {@code PipeliteHome}, one file per dead-lettered exchange.
     * Must be called before {@link PipeliteContext#start()}, which is when flows are actually
     * wired up.
     */
    void setDeadLetterQueueRepository(DeadLetterQueueRepository repository);

}
