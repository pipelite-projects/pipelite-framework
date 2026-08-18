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

import java.time.LocalDateTime;
import java.util.Optional;

public interface FlowExecutionDump {

    String getId();
    LocalDateTime getCreationTime();
    String getFlowHash();
    String getFlowName();
    String getSourceEndpointResource();
    void setLastExecutedProcessor(String processorName);
    String getLastExecutedProcessor();

    /**
     * The processor that actually threw, captured at the exact catch site — distinct from
     * {@link #getLastExecutedProcessor()}, which only ever names the previous, successful step.
     * {@code null} if the failure didn't originate inside a processor's own
     * {@code AbstractProcessorNode.process(...)} catch block (e.g. a failure at the consumer
     * itself), in which case resubmission falls back to resupplying from the flow's source.
     */
    void setFailedProcessor(String processorName);
    String getFailedProcessor();
    void setAttemptNumber(int attemptNumber);
    int getAttemptNumber();

    void setFailureException(Throwable failureException);
    Optional<Throwable> tryGetFailureException();

    /**
     * A formatted, serializable rendering of {@link #tryGetFailureException()} — that field is
     * {@code transient} and never survives serialization, so this is the only representation of
     * the failure's stack trace that a durable/dead-lettered dump can carry.
     */
    void setStackTrace(String stackTrace);
    String getStackTrace();

    /**
     * How many attempts this flow's retry channel is configured for, copied from the owning
     * flow's {@code .withRetryChannel(...)} configuration at capture time (default 3) — read by
     * {@code RetryStrategyFilter} instead of a hardcoded constant, since that filter is a single
     * shared instance serving every flow's dumps.
     */
    void setMaxAttempts(int maxAttempts);
    int getMaxAttempts();

    /**
     * The name of the owning flow's configured dead-letter flow (the value passed to
     * {@code Pipelite.defineFlow(...)} for that flow, via {@code definedFlow(flowName)}), or
     * {@code null} if none is configured — copied at capture time for the same reason as
     * {@link #getMaxAttempts()}.
     */
    void setDeadLetterFlowName(String deadLetterFlowName);
    String getDeadLetterFlowName();

}
