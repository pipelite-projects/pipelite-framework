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
package io.pipelite.dsl.definition;


import java.util.Map;

/**
 * The {@link SourceConfigurer} of a queue source, {@code fromSource("queue://name", ...)}: the
 * consumers of the queue are the concurrent consumers of the one flow that reads it, and this
 * configures how many ({@link #concurrency(int)}) and on which kind of executor ({@link
 * #executorType(ExecutorType)}). Handed out by the {@code queue} channel, the framework's own, through
 * {@code ChannelAdapter#newSourceConfigurer()}. What it configures ({@code
 * EventDrivenConsumerService}, {@code SourceConcurrencyProperties}) is the framework's own
 * default consumer machinery.
 * <p>
 * {@link #concurrency(int)}/{@link #executorType(ExecutorType)} exist <strong>only</strong> here —
 * no other adapter's own configurer (e.g. {@code KafkaSourceConfigurer}) declares them at all.
 * This is the compile-time-enforced replacement for what {@code
 * DefaultEndpointFactory#rejectSourceConcurrencyParams} still separately checks at runtime, for
 * anyone bypassing the configurer and typing {@code ?concurrency=} directly into the query string
 * of a source that is not a queue.
 */
public final class SourceConcurrencyConfigurer extends SourceConfigurer {

    // Must match io.pipelite.core.concurrent.internal.SourceConcurrencyProperties exactly: the DSL cannot depend on the
    // module that holds the consumer machinery, so the two names are written twice, and a test keeps them aligned.
    static final String CONCURRENCY = "concurrency";
    static final String EXECUTOR_TYPE = "executorType";

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
            parameters.put(CONCURRENCY, String.valueOf(concurrency));
        }
        if (executorType != null) {
            parameters.put(EXECUTOR_TYPE, executorType.name());
        }
    }

}
