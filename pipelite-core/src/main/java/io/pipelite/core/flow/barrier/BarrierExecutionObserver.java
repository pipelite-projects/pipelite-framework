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

import io.pipelite.core.consumer.internal.ExecutionObserver;
import io.pipelite.core.context.internal.IOKeys;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.dsl.barrier.Correlation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Follows the executions of the flow that reads the queue {@code queueName}, for the barriers that await it
 * (issue #150): when an execution starts it reads the keys the barriers correlate it by, from the exchange as it
 * arrived, and keeps them in a tracker that travels with the exchange; when it ends it tells the coordinator.
 * <p>
 * It never affects the flow it follows: a key that cannot be read is logged, and the barriers that wait for it
 * wait for their timeout; whatever fails here stays here.
 */
public final class BarrierExecutionObserver implements ExecutionObserver {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final String queueName;
    private final BarrierCoordinator coordinator;

    public BarrierExecutionObserver(String queueName, BarrierCoordinator coordinator) {
        this.queueName = Objects.requireNonNull(queueName, "queueName is required and cannot be null");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator is required and cannot be null");
    }

    @Override
    public Runnable started(ExchangeImpl exchange) {
        try {
            final Set<Correlation> correlations = coordinator.correlationsAwaiting(queueName);
            if (correlations.isEmpty()) {
                return null;
            }
            final Set<Pending> pendings = new LinkedHashSet<>();
            for (Correlation correlation : correlations) {
                try {
                    pendings.add(new Pending(queueName, CorrelationKeys.keyOf(correlation, exchange)));
                } catch (RuntimeException unreadable) {
                    sysLogger.warn("An execution of the flow that reads queue '{}' cannot be correlated by {}: the barriers that "
                        + "wait for it wait until their timeout. {}", queueName, correlation, unreadable.getMessage());
                }
            }
            if (pendings.isEmpty()) {
                return null;
            }
            final ExecutionTracker tracker = new ExecutionTracker(pendings);
            exchange.setProperty(IOKeys.EXECUTION_TRACKER_PROPERTY_NAME, tracker);
            return coordinator.endOf(tracker);
        } catch (RuntimeException failure) {
            sysLogger.error("The barriers could not follow an execution of the flow that reads queue '{}'", queueName, failure);
            return null;
        }
    }

}
