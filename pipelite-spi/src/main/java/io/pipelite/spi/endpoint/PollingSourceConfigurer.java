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
package io.pipelite.spi.endpoint;

import io.pipelite.dsl.definition.SourceConfigurer;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shared {@link SourceConfigurer} base for every source built on {@link
 * ScheduledPollingConsumerService} (Time, File today) — {@code initialDelay}/{@code period}/{@code
 * timeUnit}/{@code batchSize} are that shared consumer's own generic query parameters, not
 * anything adapter-specific, so they live here once instead of being duplicated on every leaf
 * configurer. Abstract, like {@link SourceConfigurer} itself: an adapter with no extra settings of
 * its own (e.g. Time) still needs its own named, empty subclass (e.g. {@code TimeSourceConfigurer})
 * rather than using this class directly — a distinct type per adapter is what lets a mismatched
 * configurer (e.g. a {@code FileSourceConfigurer} lambda supplied for a {@code time://} source)
 * still fail via the same {@link ClassCastException} safety net {@code DefaultEndpointFactory}
 * relies on, even though the two adapters' settings happen to be identical today.
 */
public abstract class PollingSourceConfigurer extends SourceConfigurer {

    private Long initialDelay;
    private Long period;
    private TimeUnit timeUnit;
    private Integer batchSize;

    public PollingSourceConfigurer initialDelay(long initialDelay) {
        this.initialDelay = initialDelay;
        return this;
    }

    public PollingSourceConfigurer period(long period) {
        this.period = period;
        return this;
    }

    public PollingSourceConfigurer timeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
        return this;
    }

    public PollingSourceConfigurer batchSize(int batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    @Override
    protected void contributeQueryParameters(Map<String, String> parameters) {
        if (initialDelay != null) {
            parameters.put(ScheduledPollingConsumerService.INITIAL_DELAY_PROPERTY_NAME, String.valueOf(initialDelay));
        }
        if (period != null) {
            parameters.put(ScheduledPollingConsumerService.PERIOD_PROPERTY_NAME, String.valueOf(period));
        }
        if (timeUnit != null) {
            parameters.put(ScheduledPollingConsumerService.TIME_UNIT_PROPERTY_NAME, timeUnit.name());
        }
        if (batchSize != null) {
            parameters.put(ScheduledPollingConsumerService.BATCH_SIZE_PROPERTY_NAME, String.valueOf(batchSize));
        }
    }

}
