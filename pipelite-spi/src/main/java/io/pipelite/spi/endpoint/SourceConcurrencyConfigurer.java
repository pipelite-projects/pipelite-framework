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
import io.pipelite.spi.flow.concurrent.ExecutorType;
import io.pipelite.spi.flow.concurrent.SourceConcurrencyProperties;

import java.util.Map;

/**
 * The {@link SourceConfigurer} of a queue source, {@code fromSource("queue://name", ...)}: the
 * consumers of the queue are the concurrent consumers of the one flow that reads it, and this
 * configures how many ({@link #concurrency(int)}) and on which kind of executor ({@link
 * #executorType(ExecutorType)}). Handed out by the {@code queue} channel adapter through {@code
 * ChannelAdapter#newSourceConfigurer()}. Lives here, next to {@code DefaultEndpoint}, rather than
 * in the {@code queue-channel-adapter} module, because what it configures ({@code
 * EventDrivenConsumerService}, {@code SourceConcurrencyProperties}) is the framework's own
 * default consumer machinery, not something {@code queue-channel-adapter} builds.
 * <p>
 * {@link #concurrency(int)}/{@link #executorType(ExecutorType)} exist <strong>only</strong> here —
 * no other adapter's own configurer (e.g. {@code KafkaSourceConfigurer}) declares them at all.
 * This is the compile-time-enforced replacement for what {@code
 * DefaultEndpointFactory#rejectSourceConcurrencyParams} still separately checks at runtime, for
 * anyone bypassing the configurer and typing {@code ?concurrency=} directly into the query string
 * of a source that is not a queue.
 */
public final class SourceConcurrencyConfigurer extends SourceConfigurer {

    private Integer concurrency;
    private ExecutorType executorType;

    public SourceConcurrencyConfigurer concurrency(int concurrency) {
        this.concurrency = concurrency;
        return this;
    }

    public SourceConcurrencyConfigurer executorType(ExecutorType executorType) {
        this.executorType = executorType;
        return this;
    }

    @Override
    protected void contributeQueryParameters(Map<String, String> parameters) {
        if (concurrency != null) {
            parameters.put(SourceConcurrencyProperties.CONCURRENCY, String.valueOf(concurrency));
        }
        if (executorType != null) {
            parameters.put(SourceConcurrencyProperties.EXECUTOR_TYPE, executorType.name());
        }
    }

}
