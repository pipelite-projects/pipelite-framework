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

import io.pipelite.core.context.internal.AbstractService;
import io.pipelite.core.concurrent.internal.DefaultThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Looks after the barriers on a schedule (issue #150): gives up the waits that took longer than their barrier
 * allows, forgets the completions nobody can need any more, and resumes what a crash, or a worker pool that
 * refused it, left resolved but not resumed. See {@link BarrierCoordinator#maintain()}.
 * <p>
 * A wait that has expired is given up at the first run after its time, so up to one period late.
 */
public final class BarrierMaintenanceService extends AbstractService {

    private static final String THREAD_ROLE = "barrier";
    private static final long STOP_TIMEOUT_MILLIS = 5_000;

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final BarrierCoordinator coordinator;
    private final Duration period;
    private ScheduledExecutorService executor;

    public BarrierMaintenanceService(BarrierCoordinator coordinator, Duration period) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator is required and cannot be null");
        this.period = Objects.requireNonNull(period, "period is required and cannot be null");
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("the period of the maintenance of the barriers must be positive, got " + period);
        }
    }

    @Override
    public void doStart() {
        executor = Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory(THREAD_ROLE));
        // scheduleWithFixedDelay, and a run that never throws: a scheduled task that throws is cancelled for good
        executor.scheduleWithFixedDelay(this::runOnce, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void doStop() {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                sysLogger.warn("The maintenance of the barriers did not stop within {}ms", STOP_TIMEOUT_MILLIS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    void runOnce() {
        try {
            coordinator.maintain();
        } catch (RuntimeException failure) {
            sysLogger.error("The maintenance of the barriers failed, it will run again", failure);
        }
    }
}
