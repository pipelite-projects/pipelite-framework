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
package io.pipelite.spi.flow.concurrent;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;

/**
 * High/low watermark (hysteresis) backpressure gate for a producer/consumer queue whose own
 * capacity is otherwise unbounded (or bounded too high to matter) — see {@code EventDrivenConsumer}
 * and {@code DefaultPollingConsumer}, both of which have this problem today (confirmed empirically:
 * 5,000,000 {@code put()} calls against a freshly constructed queue with zero consumers completed
 * without blocking at all).
 *
 * <p>A single fixed threshold ("block once full, unblock once not full") flaps a producer between
 * blocked and unblocked whenever producer and consumer rates are close to each other, wasting
 * coordination overhead on rapid re-blocking. Two thresholds instead of one avoid that: pressure
 * engages once the queue size reaches {@code highWatermark}, and only releases once the queue has
 * actually drained back down to {@code lowWatermark} — pressure is applied only while trending up,
 * released only while trending down. Same principle as TCP flow control, Netty's {@code
 * WriteBufferWaterMark}, and Reactive Streams backpressure.
 *
 * <p>This class knows nothing about the queue itself — callers pass their own queue's current size
 * as an {@link IntSupplier} at each call, read while holding this gate's lock, so the read and the
 * blocking decision are consistent with each other even though the actual add/remove against the
 * queue happens outside that lock (in the caller, immediately before/after).
 */
public class QueuePressureGate {

    private final int highWatermark;
    private final int lowWatermark;
    private final Lock lock = new ReentrantLock();
    private final Condition released = lock.newCondition();
    private volatile boolean pressured = false;

    public QueuePressureGate(int highWatermark, int lowWatermark) {
        if (lowWatermark < 0) {
            throw new IllegalArgumentException("lowWatermark must be >= 0, got " + lowWatermark);
        }
        if (highWatermark <= lowWatermark) {
            throw new IllegalArgumentException(
                "highWatermark (" + highWatermark + ") must be greater than lowWatermark (" + lowWatermark + ")");
        }
        this.highWatermark = highWatermark;
        this.lowWatermark = lowWatermark;
    }

    /**
     * {@code lowWatermark} defaults to a quarter of {@code highWatermark} (0 for a {@code
     * highWatermark} of 3 or less) — a reasonable general-purpose gap between the two thresholds
     * without needing every call site to work out its own.
     */
    public static QueuePressureGate withDefaultHysteresis(int highWatermark) {
        final int lowWatermark = highWatermark > 3 ? highWatermark / 4 : 0;
        return new QueuePressureGate(highWatermark, lowWatermark);
    }

    /**
     * Call before adding an item to the queue, passing the queue's current size (before the add).
     * Blocks the calling thread while pressure is currently engaged, regardless of the size just
     * passed — engaging is a one-way decision made here on the way up, never independently
     * re-decided by a later, possibly-stale read of size, so a burst of producer calls arriving at
     * the same instant can't each conclude "still under high, let me through" one after another.
     */
    public void beforeEnqueue(IntSupplier currentSize) throws InterruptedException {
        lock.lock();
        try {
            if (!pressured && currentSize.getAsInt() >= highWatermark) {
                pressured = true;
            }
            while (pressured) {
                released.await();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Call after removing an item from the queue, passing the queue's current size (after the
     * removal). Releases pressure — and wakes every producer blocked in {@link #beforeEnqueue} —
     * once the queue has drained down to {@code lowWatermark} or below.
     */
    public void afterDequeue(IntSupplier currentSize) {
        if (!pressured) {
            return;
        }
        lock.lock();
        try {
            if (pressured && currentSize.getAsInt() <= lowWatermark) {
                pressured = false;
                released.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isPressured() {
        return pressured;
    }
}
