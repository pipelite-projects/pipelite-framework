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
package io.pipelite.core.consumer.internal;

import io.pipelite.core.consumer.internal.EventDrivenConsumer.PriorityExchange;
import io.pipelite.core.correlation.CorrelationKeys;
import io.pipelite.dsl.correlation.Correlation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Keeps the exchanges that have the same key from running together (issue #156): the first exchange of a key
 * to be admitted owns the key until it has finished, and the ones that follow wait behind it, in the order they
 * were admitted. Exchanges of different keys never wait for each other. The key is read the way a barrier reads
 * its own.
 * <p>
 * A key is busy from the moment its owner is admitted to the moment {@link #release} is called; when the owner
 * finishes and others wait, the next one becomes the owner without the key ever being free, so nothing can
 * overtake it. A key with nothing running and nothing waiting is forgotten, so what is kept is bounded by the
 * keys in flight and by the exchanges that wait.
 * <p>
 * Admission is called by one thread at a time (the dispatcher), in the order the exchanges left the queue: it
 * is that order that is kept.
 */
final class OrderedAdmission {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final Correlation correlation;
    private final String flowName;

    private final Object lock = new Object();
    /** The busy keys: who owns each one, and the exchanges that wait behind the owner. */
    private final Map<String, Slot> busy = new HashMap<>();
    private int waitingCount;

    private static final class Slot {
        private String owner;
        private final Deque<PriorityExchange> behind = new ArrayDeque<>();

        private Slot(String owner) {
            this.owner = owner;
        }
    }

    /** The exchange that owns a key is identified by its input message. */
    static String ownerOf(PriorityExchange item) {
        return item.getExchange().getInput().getId();
    }

    OrderedAdmission(Correlation correlation, String flowName) {
        this.correlation = Objects.requireNonNull(correlation, "correlation is required and cannot be null");
        this.flowName = flowName;
    }

    /** The key of {@code item}, or {@code null} when the exchange has none: it is then processed without ordering. */
    String keyOf(PriorityExchange item) {
        try {
            return CorrelationKeys.keyOf(correlation, item.getExchange());
        } catch (RuntimeException unreadable) {
            sysLogger.warn("Flow '{}': an exchange has no key to be ordered by ({}), it is processed without ordering. {}",
                flowName, correlation, unreadable.getMessage());
            return null;
        }
    }

    /**
     * Admits {@code item}, which has {@code key}. {@code true} when it may run now, and then the key is busy until
     * {@link #release}; {@code false} when its key is busy: it is kept, and comes back from {@link #release}.
     */
    boolean admit(String key, PriorityExchange item) {
        synchronized (lock) {
            final Slot slot = busy.get(key);
            if (slot == null) {
                busy.put(key, new Slot(ownerOf(item)));
                return true;
            }
            if (slot.owner.equals(ownerOf(item))) {
                // The owner itself, handed back to the source to start again: it already holds the key.
                return true;
            }
            slot.behind.addLast(item);
            waitingCount++;
            return false;
        }
    }

    /**
     * {@code key} is held by the exchange {@code owner}, which is not in the queue: it was waiting for a retry or at a
     * barrier when the process stopped (see {@link HeldKeys}). The exchanges of the key that come now wait for it.
     * Does nothing if the key is held already.
     */
    void reserve(String key, String owner) {
        synchronized (lock) {
            busy.putIfAbsent(key, new Slot(owner));
        }
    }

    /**
     * The exchange {@code owner} has finished with {@code key}. Returns the one that waited the longest, which now
     * owns the key and has to run, or {@code null} when nothing waited and the key is free. Letting go of a key that
     * {@code owner} does not own, because it already did or because the process restarted since, does nothing.
     */
    PriorityExchange release(String key, String owner) {
        synchronized (lock) {
            final Slot slot = busy.get(key);
            if (slot == null || !slot.owner.equals(owner)) {
                return null;
            }
            final PriorityExchange next = slot.behind.pollFirst();
            if (next == null) {
                busy.remove(key);
                return null;
            }
            waitingCount--;
            slot.owner = ownerOf(next);
            return next;
        }
    }

    /** How many exchanges wait behind a key. They count as queued for the backpressure of the source. */
    int waitingCount() {
        synchronized (lock) {
            return waitingCount;
        }
    }
}
