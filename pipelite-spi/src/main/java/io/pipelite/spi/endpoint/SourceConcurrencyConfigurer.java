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
 * The {@link SourceConfigurer} for a no-protocol, internal source — a plain resource name with no
 * {@code scheme://} prefix (e.g. {@code .fromSource("destination")}), always built as a bare
 * {@link DefaultEndpoint} by {@code DefaultEndpointFactory}'s no-protocol fallback branch, never
 * through any {@code ChannelAdapter}. In practice this is always the receiving end of another
 * flow's {@code .toSink("link://name")} hop — confirmed there is no other way to reach a
 * no-protocol source, and {@code link://} itself has no consumer/source side at all ({@code
 * LinkEndpoint} implements only {@code createProducer()}). Named for what it actually configures
 * — {@link #concurrency(int)}/{@link #executorType(ExecutorType)} — rather than for {@code
 * link://}, which it has no real tie to. Lives here, next to {@code DefaultEndpoint}, rather than
 * in the {@code link-channel-adapter} module, precisely because it configures the framework's own
 * default endpoint, not anything {@code link-channel-adapter} builds.
 * <p>
 * {@link #concurrency(int)}/{@link #executorType(ExecutorType)} exist <strong>only</strong> here —
 * no protocol-backed adapter's own configurer (e.g. {@code KafkaSourceConfigurer}) declares them
 * at all. This is the compile-time-enforced replacement for what {@code
 * DefaultEndpointFactory#rejectSourceConcurrencyParams} still separately checks at runtime, for
 * anyone bypassing the configurer and typing {@code ?concurrency=} directly into a protocol-backed
 * URL's query string by hand.
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
