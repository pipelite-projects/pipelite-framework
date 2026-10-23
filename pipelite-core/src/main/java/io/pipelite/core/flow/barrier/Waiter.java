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

import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * An exchange parked at a barrier, until the flows it awaits have completed.
 * <p>
 * It keeps the whole exchange, serialized like the one of a retry dump or a dead letter, so that what travels with
 * it (the return address, the routing slip) survives the wait. {@link #pending()} is what it still waits for;
 * when it is empty the wait is over, and the status says how: {@link Status#RELEASED} resumes the flow on the
 * node after the barrier, {@link Status#FAILED} hands it to the error handling. A resolved waiter stays until the
 * flow has dealt with it, so a crash in between finds it again.
 * <p>
 * Immutable: a change makes a new instance.
 */
public record Waiter(String id, String barrierId, String flowName, String nodeName, String exchangeData,
                     String encoding, Set<Pending> pending, Status status, Pending failedBy, Instant createdAt,
                     Instant expiresAt) implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        WAITING, RELEASED, FAILED
    }

    public Waiter {
        Objects.requireNonNull(id, "id is required and cannot be null");
        Objects.requireNonNull(barrierId, "barrierId is required and cannot be null");
        Objects.requireNonNull(flowName, "flowName is required and cannot be null");
        Objects.requireNonNull(nodeName, "nodeName is required and cannot be null");
        Objects.requireNonNull(exchangeData, "exchangeData is required and cannot be null");
        Objects.requireNonNull(encoding, "encoding is required and cannot be null");
        Objects.requireNonNull(status, "status is required and cannot be null");
        Objects.requireNonNull(createdAt, "createdAt is required and cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt is required and cannot be null");
        pending = pending == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(pending));
    }

    /** A waiter that has just arrived, waiting for all of {@code pending}. */
    public static Waiter waiting(String id, String barrierId, String flowName, String nodeName, String exchangeData,
                                 String encoding, Set<Pending> pending, Instant createdAt, Instant expiresAt) {
        return new Waiter(id, barrierId, flowName, nodeName, exchangeData, encoding, pending, Status.WAITING, null,
            createdAt, expiresAt);
    }

    public Waiter withPending(Set<Pending> remaining) {
        return new Waiter(id, barrierId, flowName, nodeName, exchangeData, encoding, remaining, status, failedBy,
            createdAt, expiresAt);
    }

    /** What it waited for has completed. */
    public Waiter released() {
        return new Waiter(id, barrierId, flowName, nodeName, exchangeData, encoding, Set.of(), Status.RELEASED, null,
            createdAt, expiresAt);
    }

    /** A flow it waited for failed. */
    public Waiter failed(Pending by) {
        return new Waiter(id, barrierId, flowName, nodeName, exchangeData, encoding, Set.of(), Status.FAILED, by,
            createdAt, expiresAt);
    }
}
