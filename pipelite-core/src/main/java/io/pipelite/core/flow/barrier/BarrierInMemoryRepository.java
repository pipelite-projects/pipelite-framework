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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps the barriers in memory: a restart loses every exchange that was waiting. It holds all the logic of a
 * {@link BarrierRepository}, under one lock, since every operation is short and has to see the waiters and the
 * completions together; a subclass that makes it durable (see {@code BarrierFileRepository}) only hears about each
 * change, through the {@code on...} methods, which run under that lock, before the operation returns.
 */
public class BarrierInMemoryRepository implements BarrierRepository {

    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, Waiter> waiters = new LinkedHashMap<>();
    private final Map<Pending, Completion> completions = new HashMap<>();
    /** The waiters that are still waiting, by what they wait for: where a completion finds them. */
    private final Map<Pending, Set<String>> waitingOn = new HashMap<>();

    @Override
    public WaitDisposition arrive(Waiter waiter) {
        Objects.requireNonNull(waiter, "waiter is required and cannot be null");
        if (waiter.status() != Waiter.Status.WAITING) {
            throw new IllegalArgumentException("only a waiting waiter can arrive, got " + waiter.status());
        }
        lock.lock();
        try {
            final Waiter existing = waiters.get(waiter.id());
            if (existing != null) {
                // An arrival replayed after a crash: still waiting is the same wait, any other state belongs to
                // the recovery, which resumes it.
                return existing.status() == Waiter.Status.WAITING ? WaitDisposition.hold() : WaitDisposition.discard();
            }
            final Set<Pending> remaining = new LinkedHashSet<>();
            for (Pending pending : waiter.pending()) {
                final Completion completion = completions.get(pending);
                if (completion == null) {
                    remaining.add(pending);
                } else if (completion.outcome() == Completion.Outcome.FAILED) {
                    return WaitDisposition.fail(pending);
                }
            }
            if (remaining.isEmpty()) {
                return WaitDisposition.proceed();
            }
            put(null, waiter.withPending(remaining));
            return WaitDisposition.hold();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Waiter> complete(String flow, String key, Instant retainUntil) {
        final Pending of = new Pending(flow, key);
        lock.lock();
        try {
            if (completions.containsKey(of)) {
                return List.of();
            }
            putCompletion(new Completion(of, Completion.Outcome.COMPLETED, retainUntil));
            final List<Waiter> released = new ArrayList<>();
            for (String id : idsWaitingOn(of)) {
                final Waiter waiter = waiters.get(id);
                final Set<Pending> remaining = new LinkedHashSet<>(waiter.pending());
                remaining.remove(of);
                if (remaining.isEmpty()) {
                    final Waiter resolved = waiter.released();
                    put(waiter, resolved);
                    released.add(resolved);
                } else {
                    put(waiter, waiter.withPending(remaining));
                }
            }
            return released;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Waiter> fail(String flow, String key, Instant retainUntil) {
        final Pending of = new Pending(flow, key);
        lock.lock();
        try {
            if (completions.containsKey(of)) {
                return List.of();
            }
            putCompletion(new Completion(of, Completion.Outcome.FAILED, retainUntil));
            final List<Waiter> failed = new ArrayList<>();
            for (String id : idsWaitingOn(of)) {
                final Waiter waiter = waiters.get(id);
                final Waiter resolved = waiter.failed(of);
                put(waiter, resolved);
                failed.add(resolved);
            }
            return failed;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<Waiter> tryLoad(String waiterId) {
        lock.lock();
        try {
            return Optional.ofNullable(waiters.get(waiterId));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Waiter> findResolved() {
        lock.lock();
        try {
            return waiters.values().stream().filter(waiter -> waiter.status() != Waiter.Status.WAITING).toList();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<Waiter> expire(Instant now) {
        lock.lock();
        try {
            final List<Waiter> expired = new ArrayList<>();
            for (Waiter waiter : new ArrayList<>(waiters.values())) {
                if (waiter.status() == Waiter.Status.WAITING && !waiter.expiresAt().isAfter(now)) {
                    final Waiter resolved = waiter.expired();
                    put(waiter, resolved);
                    expired.add(resolved);
                }
            }
            return expired;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void remove(String waiterId) {
        lock.lock();
        try {
            final Waiter waiter = waiters.remove(waiterId);
            if (waiter != null) {
                unindex(waiter);
                onWaiterRemoved(waiter);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int purgeCompletions(Instant now) {
        lock.lock();
        try {
            final List<Completion> expired = completions.values().stream()
                .filter(completion -> !completion.retainUntil().isAfter(now))
                .toList();
            for (Completion completion : expired) {
                completions.remove(completion.of());
                onCompletionRemoved(completion);
            }
            return expired.size();
        } finally {
            lock.unlock();
        }
    }

    // ---- what a durable subclass hears, under the lock, before the operation returns ----

    protected void onWaiterStored(Waiter waiter) {
    }

    protected void onWaiterRemoved(Waiter waiter) {
    }

    protected void onCompletionStored(Completion completion) {
    }

    protected void onCompletionRemoved(Completion completion) {
    }

    /**
     * Puts back what a durable subclass read from its storage, without telling it. Then {@link #reconcile()}, once,
     * for what a crash left half done: a completion written, but not yet applied to every waiter.
     */
    protected final void restore(Waiter waiter) {
        lock.lock();
        try {
            put(null, waiter, false);
        } finally {
            lock.unlock();
        }
    }

    protected final void restore(Completion completion) {
        lock.lock();
        try {
            completions.put(completion.of(), completion);
        } finally {
            lock.unlock();
        }
    }

    protected final void reconcile() {
        lock.lock();
        try {
            for (Waiter waiter : new ArrayList<>(waiters.values())) {
                if (waiter.status() != Waiter.Status.WAITING) {
                    continue;
                }
                final Set<Pending> remaining = new LinkedHashSet<>();
                Pending failedBy = null;
                for (Pending pending : waiter.pending()) {
                    final Completion completion = completions.get(pending);
                    if (completion == null) {
                        remaining.add(pending);
                    } else if (completion.outcome() == Completion.Outcome.FAILED && failedBy == null) {
                        failedBy = pending;
                    }
                }
                if (failedBy != null) {
                    put(waiter, waiter.failed(failedBy));
                } else if (remaining.isEmpty()) {
                    put(waiter, waiter.released());
                } else if (remaining.size() != waiter.pending().size()) {
                    put(waiter, waiter.withPending(remaining));
                }
            }
        } finally {
            lock.unlock();
        }
    }

    // ---- internals, always under the lock ----

    private void putCompletion(Completion completion) {
        completions.put(completion.of(), completion);
        onCompletionStored(completion);
    }

    private void put(Waiter previous, Waiter updated) {
        put(previous, updated, true);
    }

    private void put(Waiter previous, Waiter updated, boolean notify) {
        if (previous != null) {
            unindex(previous);
        }
        waiters.put(updated.id(), updated);
        if (updated.status() == Waiter.Status.WAITING) {
            for (Pending pending : updated.pending()) {
                waitingOn.computeIfAbsent(pending, key -> new LinkedHashSet<>()).add(updated.id());
            }
        }
        if (notify) {
            onWaiterStored(updated);
        }
    }

    private void unindex(Waiter waiter) {
        for (Pending pending : waiter.pending()) {
            final Set<String> ids = waitingOn.get(pending);
            if (ids != null) {
                ids.remove(waiter.id());
                if (ids.isEmpty()) {
                    waitingOn.remove(pending);
                }
            }
        }
    }

    private List<String> idsWaitingOn(Pending pending) {
        return new ArrayList<>(waitingOn.getOrDefault(pending, Set.of()));
    }
}
