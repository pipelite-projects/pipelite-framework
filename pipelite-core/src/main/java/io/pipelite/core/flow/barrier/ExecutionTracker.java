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
 * failure handled anywhere down the flow is seen where the run ends. A run can have more than one exchange in it:
 * the elements of a split continue through the flow as exchanges of their own and share the tracker of the run
 * (issue #149), so the outcomes are counted for each exchange, not kept as one state that the last one overwrites.
 * It is not serialized: a retry, or the wait at a barrier, that writes the exchange away and reads it back starts a
 * new run, with the same pairs and a clean state.
 * <p>
 * When the run ends:
 * <ul>
 *     <li>no exchange failed for good, and none is retrying or waiting: the execution completed. An exchange dropped
 *     by a filter or stopped by the user counts, since it did not fail.</li>
 *     <li>an exchange has a failure handed to the error handling of the flow, and it is not to be retried: the
 *     execution failed. It is definitive, and it is told even if another exchange of the run is retrying.</li>
 *     <li>otherwise, an exchange was saved to be retried, or waits at a barrier of this same flow: the run has not
 *     ended, nothing is told yet. The retry is a new run that will end, or fail again, or exhaust the attempts.</li>
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

    /**
     * The key a source that orders holds while this execution is not over (issue #156): the flow, the key, and the
     * exchange that owns it. It travels with the exchange, so the key is let go when the execution ends wherever it
     * does: on the thread of the flow, on the retry thread, after a restart.
     */
    public record Ordering(String flowName, String key, String owner) implements Serializable {
    }

    private final Set<Pending> pendings;
    private final Ordering ordering;
    // All of them are zero after the exchange was read back from storage: a new run, which is running.
    private transient int failed;
    private transient int retrying;
    private transient int waiting;
    private transient boolean settled;

    public ExecutionTracker(Set<Pending> pendings) {
        this(pendings, null);
    }

    public ExecutionTracker(Set<Pending> pendings, Ordering ordering) {
        this.pendings = Set.copyOf(pendings);
        this.ordering = ordering;
    }

    /** The tracker of the run {@code exchange} is in, or {@code null} when no barrier awaits this flow. */
    public static ExecutionTracker of(ExchangeImpl exchange) {
        return exchange.getProperty(IOKeys.EXECUTION_TRACKER_PROPERTY_NAME, ExecutionTracker.class);
    }

    /** A failure of the run was handed to the error handling of the flow. */
    public static void markFailed(ExchangeImpl exchange) {
        final ExecutionTracker tracker = of(exchange);
        if (tracker != null) {
            tracker.failed();
        }
    }

    /** The failure was saved to be retried: it is not a definitive one. */
    public static void markRetrying(ExchangeImpl exchange) {
        final ExecutionTracker tracker = of(exchange);
        if (tracker != null) {
            tracker.retrying();
        }
    }

    /** The exchange waits at a barrier of its own flow: this run has not ended, however it returns. */
    public static void markWaiting(ExchangeImpl exchange) {
        final ExecutionTracker tracker = of(exchange);
        if (tracker != null) {
            tracker.waiting();
        }
    }

    /** The flows and keys the barriers wait for. */
    public Set<Pending> pendings() {
        return pendings;
    }

    /** The key of an ordered source that this execution holds, or {@code null}. */
    public Ordering ordering() {
        return ordering;
    }

    /** The run ended: what to tell. Only once: a second call tells nothing. */
    public synchronized Outcome settle() {
        if (settled) {
            return Outcome.NONE;
        }
        settled = true;
        if (failed > 0) {
            return Outcome.FAILED;
        }
        return retrying > 0 || waiting > 0 ? Outcome.NONE : Outcome.COMPLETED;
    }

    /** The attempts to retry the failure are exhausted: it is definitive. Only once. */
    public synchronized Outcome settleExhausted() {
        final boolean already = settled;
        settled = true;
        return already ? Outcome.NONE : Outcome.FAILED;
    }

    private synchronized void failed() {
        if (!settled) {
            failed++;
        }
    }

    /** The failure of an exchange that was counted as failed is to be retried: it is not a definitive one. */
    private synchronized void retrying() {
        if (!settled) {
            if (failed > 0) {
                failed--;
            }
            retrying++;
        }
    }

    private synchronized void waiting() {
        if (!settled) {
            waiting++;
        }
    }
}
