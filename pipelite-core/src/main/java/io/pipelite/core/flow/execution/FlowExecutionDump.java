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

    /**
     * What {@code RetryStrategyFilter} does once a dump's attempts are exhausted (issue #91).
     * {@code NONE} is the pre-#91 default (log and discard, see issue #87) - reachable today only
     * as a defensive fallback, since the DSL now requires every {@code .withRetry(...)} to
     * declare an exhaustion action via {@code .onErrorChannel(...)}/{@code .onExceptionHandler(...)}.
     */
    enum ExhaustionAction {
        DEAD_LETTER_CHANNEL, BUILT_IN_DLQ, FLOW_EXCEPTION_HANDLER, NONE
    }

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
     * The owning flow's declared exhaustion action (issue #91) - copied at capture time for the
     * same reason as {@link #getMaxAttempts()}, since {@code RetryStrategyFilter} is a single
     * shared instance serving every flow's dumps.
     */
    void setExhaustionAction(ExhaustionAction exhaustionAction);
    ExhaustionAction getExhaustionAction();

    /**
     * The dead-letter target - a URL: {@code queue://<queue name>} for an internal flow or
     * a channel adapter URL for an external system, see
     * {@code io.pipelite.dsl.definition.builder.error.ErrorChannelOperations#toChannel} - only
     * meaningful when {@link #getExhaustionAction()} is {@link ExhaustionAction#DEAD_LETTER_CHANNEL},
     * {@code null} otherwise.
     */
    void setDeadLetterTarget(String deadLetterTarget);
    String getDeadLetterTarget();

    /**
     * This dump's own claim state — see {@link FlowExecutionDumpStatus}. A newly created dump
     * always starts {@link FlowExecutionDumpStatus#PENDING}; callers should not set this directly
     * to claim a dump, that's what {@link FlowExecutionDumpRepository#tryClaim(String)} is for —
     * this setter exists for repository implementations parsing a dump back from storage.
     */
    void setStatus(FlowExecutionDumpStatus status);
    FlowExecutionDumpStatus getStatus();

}
