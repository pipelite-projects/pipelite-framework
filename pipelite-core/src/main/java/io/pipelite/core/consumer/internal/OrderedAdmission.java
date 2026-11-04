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
    /** The busy keys, each with the exchanges that wait behind its owner. */
    private final Map<String, Deque<PriorityExchange>> waiting = new HashMap<>();
    private int waitingCount;

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
            final Deque<PriorityExchange> behind = waiting.get(key);
            if (behind == null) {
                waiting.put(key, new ArrayDeque<>());
                return true;
            }
            behind.addLast(item);
            waitingCount++;
            return false;
        }
    }

    /**
     * The exchange that owned {@code key} has finished. Returns the one that waited the longest, which now owns
     * the key and has to run, or {@code null} when nothing waited and the key is free.
     */
    PriorityExchange release(String key) {
        synchronized (lock) {
            final Deque<PriorityExchange> behind = waiting.get(key);
            if (behind == null) {
                return null;
            }
            final PriorityExchange next = behind.pollFirst();
            if (next == null) {
                waiting.remove(key);
                return null;
            }
            waitingCount--;
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
