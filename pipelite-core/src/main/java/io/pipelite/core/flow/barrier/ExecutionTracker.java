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
package io.pipelite.core.flow.barrier;

import io.pipelite.core.context.internal.IOKeys;
import io.pipelite.core.exchange.internal.ExchangeImpl;

import java.io.Serializable;
import java.util.Set;

/**
 * What became of an execution of a flow that a barrier awaits (issue #150), carried by the exchange as a property:
 * the pairs, flow and key, that the barriers wait for, and how the run is going.
 * <p>
 * The state is shared by every copy of the exchange within a run, since a property is copied by reference, so a
 * failure handled anywhere down the flow is seen where the run ends. It is not serialized: a retry, or the wait at
 * a barrier, that writes the exchange away and reads it back starts a new run, with the same pairs and a clean
 * state.
 * <ul>
 *     <li>{@code RUNNING}, and it ends: the execution completed. An exchange dropped by a filter or stopped by
 *     the user counts, since it did not fail.</li>
 *     <li>{@code FAILED}: a failure was handed to the error handling of the flow. It is definitive, unless the
 *     handler says it is {@code RETRYING}.</li>
 *     <li>{@code RETRYING}: the failure was saved to be retried. The retry is a new run that will end, or fail
 *     again, or exhaust the attempts: nothing is told yet.</li>
 *     <li>{@code WAITING}: the exchange waits at a barrier of this same flow, so the execution has not ended.</li>
 * </ul>
 */
public final class ExecutionTracker implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Outcome {
        /** The run completed: the barriers that await this flow for these keys are told so. */
        COMPLETED,
        /** The run failed definitively. */
        FAILED,
        /** Nothing to tell: the run was retried or is waiting, or it was already settled. */
        NONE
    }

    private enum State {
        RUNNING, FAILED, RETRYING, WAITING, SETTLED
    }

    private final Set<Pending> pendings;
    /** {@code null} after the exchange was read back from storage: a new run, which is running. */
    private transient volatile State state;

    public ExecutionTracker(Set<Pending> pendings) {
        this.pendings = Set.copyOf(pendings);
    }

    /** The tracker of the run {@code exchange} is in, or {@code null} when no barrier awaits this flow. */
    public static ExecutionTracker of(ExchangeImpl exchange) {
        return exchange.getProperty(IOKeys.EXECUTION_TRACKER_PROPERTY_NAME, ExecutionTracker.class);
    }

    /** A failure of the run was handed to the error handling of the flow. */
    public static void markFailed(ExchangeImpl exchange) {
        final ExecutionTracker tracker = of(exchange);
        if (tracker != null) {
            tracker.move(State.FAILED);
        }
    }

    /** The failure was saved to be retried: it is not a definitive one. */
    public static void markRetrying(ExchangeImpl exchange) {
        final ExecutionTracker tracker = of(exchange);
        if (tracker != null) {
            tracker.move(State.RETRYING);
        }
    }

    /** The exchange waits at a barrier of its own flow: this run has not ended, however it returns. */
    public static void markWaiting(ExchangeImpl exchange) {
        final ExecutionTracker tracker = of(exchange);
        if (tracker != null) {
            tracker.move(State.WAITING);
        }
    }

    /** The flows and keys the barriers wait for. */
    public Set<Pending> pendings() {
        return pendings;
    }

    /** The run ended: what to tell. Only once: a second call, and a run that was retried or waits, tell nothing. */
    public synchronized Outcome settle() {
        final State current = state == null ? State.RUNNING : state;
        state = State.SETTLED;
        return switch (current) {
            case RUNNING -> Outcome.COMPLETED;
            case FAILED -> Outcome.FAILED;
            default -> Outcome.NONE;
        };
    }

    /** The attempts to retry the failure are exhausted: it is definitive. Only once. */
    public synchronized Outcome settleExhausted() {
        final boolean already = state == State.SETTLED;
        state = State.SETTLED;
        return already ? Outcome.NONE : Outcome.FAILED;
    }

    private synchronized void move(State next) {
        if (state != State.SETTLED) {
            state = next;
        }
    }
}
