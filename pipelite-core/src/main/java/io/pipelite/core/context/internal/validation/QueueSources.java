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
package io.pipelite.core.context.internal.validation;

import io.pipelite.core.definition.TypedSourceDefinition;
import io.pipelite.dsl.ChannelProtocols;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.definition.SourceDefinition;
import io.pipelite.spi.channel.ChannelURL;
import io.pipelite.spi.endpoint.EndpointURL;

import java.util.Optional;

/**
 * Which flows a {@code queue://} destination reaches: the ones whose source is a queue. That is
 * what {@code QueueChannelAdapter} registers a consumer for, so {@code queue://x} resolves to the
 * flow that declares {@code fromSource("queue://x")} and to no other. A source of any other
 * protocol is never an address of this kind.
 */
final class QueueSources {

    private QueueSources() {
    }

    /**
     * The name of the queue {@code flow}'s source reads, resolved the way the endpoint factory
     * resolves it ({@code ${...}} placeholders replaced, query parameters ignored), or empty if its
     * source is not a queue, is a framework-built typed source, or cannot be resolved - which fails
     * on its own, clearly, when the flow is registered, so it is not a finding of the validators.
     */
    static Optional<String> nameOf(FlowDefinition flow, ValidationContext context) {
        final SourceDefinition source = flow.sourceDefinition();
        if (source == null || source instanceof TypedSourceDefinition) {
            return Optional.empty();
        }
        try {
            final ChannelURL channelURL = ChannelURL.parse(context.resolveURL(source.getUrl()));
            if (!channelURL.hasProtocol() || !ChannelProtocols.QUEUE.equals(channelURL.getProtocol())) {
                return Optional.empty();
            }
            return Optional.of(EndpointURL.parse(channelURL.getEndpointURL()).getResource());
        } catch (RuntimeException unresolvable) {
            return Optional.empty();
        }
    }

}
