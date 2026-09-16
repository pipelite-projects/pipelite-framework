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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed, per-{@code fromSource(...)}-call alternative to writing {@code EndpointURL} query-string
 * parameters by hand — see the source-endpoint-configuration analysis in the
 * {@code pipelite-framework-analysis} repository for the full design discussion. A concrete
 * subclass lives alongside whichever {@code ChannelAdapter} it configures (e.g. {@code
 * KafkaSourceConfigurer} in the kafka-channel-adapter module) and adds its own adapter-specific
 * setters via {@link #contributeQueryParameters(Map)}.
 * <p>
 * Declared here (in {@code pipelite-dsl}), not in {@code pipelite-spi}, purely because of module
 * dependency direction — {@code pipelite-spi} already depends on {@code pipelite-dsl} (for {@code
 * Headers}), never the other way around, and {@code FlowOperations.fromSource(String, Consumer)}
 * is a {@code pipelite-dsl} type that must be able to reference this class in its own signature.
 * {@link #durableInbox(boolean)} is universal (issue #70's durable inbox applies to any source
 * regardless of adapter), so it lives on this shared base rather than being repeated per adapter.
 * {@code concurrency}/{@code executorType} deliberately do NOT live here: only the no-protocol/
 * internal source case may configure them (see {@code SourceConcurrencyConfigurer} in {@code
 * pipelite-spi}, next to {@code DefaultEndpoint}) — every protocol-backed adapter's own configurer
 * simply has no such methods, a compile-time-enforced version of the restriction {@code
 * DefaultEndpointFactory#rejectSourceConcurrencyParams} still separately enforces at runtime for
 * anyone bypassing the configurer and typing the query string by hand.
 * <p>
 * Not a lambda/functional-interface itself — {@code fromSource(url, (KafkaSourceConfigurer c) -&gt;
 * c.groupId(...).autoOffsetReset(...))} passes a {@code java.util.function.Consumer<C>} whose
 * single parameter {@code c} is an instance of this class (or a subclass), constructed by the
 * framework (via {@code ChannelAdapter#newSourceConfigurer()}, or directly for the no-protocol
 * case) and handed to the callback — mirrors {@code ChannelConfigurer}'s own "framework
 * constructs, caller mutates" shape, just letting the caller use the concrete adapter type
 * directly as the lambda's declared parameter type instead of needing an outer cast.
 */
public abstract class SourceConfigurer {

    // Must match io.pipelite.spi.inbox.DurableInboxProperties.DURABLE_INBOX exactly - duplicated
    // as a literal rather than referenced, since pipelite-dsl cannot depend on pipelite-spi.
    private static final String DURABLE_INBOX_PROPERTY_NAME = "durableInbox";

    private Boolean durableInboxEnabled;

    /**
     * Opts this source in or out of the durable inbox (default: enabled, same as today's {@code
     * ?durableInbox=false} query parameter). Available on every concrete configurer, unlike
     * {@code concurrency}/{@code executorType} - see this class's own Javadoc for why.
     */
    public final void durableInbox(boolean enabled) {
        this.durableInboxEnabled = enabled;
    }

    /**
     * Lowers whatever was set on this configurer into the exact same {@code key=value} shape
     * {@code EndpointURL}'s query string already uses, so every existing downstream reader
     * ({@code EventDrivenConsumerService}, {@code FlowFactory#wireDurableInbox}, each adapter's
     * own property reads) keeps working completely unchanged - a configurer is sugar over the
     * same mechanism, not a parallel one. Called by {@code DefaultEndpointFactory} once the real
     * endpoint URL (and, for protocol-backed sources, the resolved {@code ChannelAdapter}) is
     * known; never called per-exchange.
     */
    public final Map<String, String> toQueryParameters() {
        final Map<String, String> parameters = new LinkedHashMap<>();
        if (durableInboxEnabled != null) {
            parameters.put(DURABLE_INBOX_PROPERTY_NAME, String.valueOf(durableInboxEnabled));
        }
        contributeQueryParameters(parameters);
        return parameters;
    }

    /**
     * Overridden by each concrete adapter-specific configurer to add its own settings. No-op by
     * default so a configurer with nothing but {@link #durableInbox(boolean)} needs no override.
     */
    protected void contributeQueryParameters(Map<String, String> parameters) {
    }

}
