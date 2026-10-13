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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Where one correlation stands in a barrier: the contributions that arrived, in arrival order, and how it
 * was closed. The key of a state is the barrier ({@code <flow>/<node name>}) and the correlation key.
 * <p>
 * A state is {@link Status#OPEN} while it waits. Once it is complete, or a contribution said it failed, it is
 * closed by the contributor whose arrival closed it, and stays {@link Status#COMPLETED} or {@link
 * Status#FAILED} until the rest of the flow has handled it: if the process dies in between, the arrival of the
 * same contributor, replayed from the inbox, finds the closed state and releases again. Afterwards it is
 * {@link Status#DONE}, a tombstone with no contributions that lasts as long as the barrier could have waited,
 * so the contributions that arrive late (the others, after a failure; a replayed one, after the release) are
 * ignored instead of starting a barrier that would never complete.
 * <p>
 * Not thread-safe by itself: a {@link BarrierRepository} changes it under a lock per key.
 */
public final class BarrierState implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Status {
        OPEN, COMPLETED, FAILED, DONE
    }

    private final String barrierId;
    private final String key;
    private final Instant createdAt;
    private final Instant expiresAt;
    private final Map<String, BarrierContribution> contributions = new LinkedHashMap<>();
    private Status status = Status.OPEN;
    private String closedBy;

    private BarrierState(String barrierId, String key, Instant createdAt, Instant expiresAt) {
        this.barrierId = barrierId;
        this.key = key;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** A state that starts waiting at {@code createdAt}, to be given up after {@code timeout}. */
    public static BarrierState open(String barrierId, String key, Instant createdAt, Duration timeout) {
        Objects.requireNonNull(barrierId, "barrierId is required and cannot be null");
        Objects.requireNonNull(key, "key is required and cannot be null");
        Objects.requireNonNull(createdAt, "createdAt is required and cannot be null");
        Objects.requireNonNull(timeout, "timeout is required and cannot be null");
        return new BarrierState(barrierId, key, createdAt, createdAt.plus(timeout));
    }

    public String getBarrierId() {
        return barrierId;
    }

    public String getKey() {
        return key;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** When the state is given up: an open one goes to the dead letter queue, a done one is forgotten. */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Status getStatus() {
        return status;
    }

    /** The contributor whose arrival closed the state, {@code null} while it is open. */
    public String getClosedBy() {
        return closedBy;
    }

    public boolean hasContributor(String contributorId) {
        return contributions.containsKey(contributorId);
    }

    /** The contributors that arrived, in arrival order. */
    public Set<String> contributors() {
        return new LinkedHashSet<>(contributions.keySet());
    }

    /** The contributions, in arrival order. */
    public List<BarrierContribution> contributions() {
        return new ArrayList<>(contributions.values());
    }

    public void add(BarrierContribution contribution) {
        if (status != Status.OPEN) {
            throw new IllegalStateException("a " + status + " state does not take contributions");
        }
        contributions.put(contribution.getContributorId(), contribution);
    }

    /** Closes an open state, as {@link Status#COMPLETED} or {@link Status#FAILED}, on the arrival of {@code contributorId}. */
    public void close(Status closedStatus, String contributorId) {
        if (status != Status.OPEN || (closedStatus != Status.COMPLETED && closedStatus != Status.FAILED)) {
            throw new IllegalStateException("cannot go from " + status + " to " + closedStatus);
        }
        this.status = closedStatus;
        this.closedBy = contributorId;
    }

    /** The rest of the flow has handled the state: it keeps only the fact that it was closed. */
    public void markDone() {
        this.status = Status.DONE;
        this.contributions.clear();
    }
}
