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
package io.pipelite.components.link;

import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.context.ContextEventListener;
import io.pipelite.spi.endpoint.*;
import io.pipelite.spi.flow.Flow;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LinkChannelAdapter implements ChannelAdapter, ContextEventListener {

    private final Map<String, Consumer> consumers;

    public LinkChannelAdapter() {
        this.consumers = new HashMap<>();
    }

    @Override
    public Endpoint createEndpoint(String url) {
        return new LinkEndpoint(EndpointURL.parse(url), this);
    }

    public Optional<Consumer> tryResolveConsumer(String sourceEndpointURI){
        return Optional.ofNullable(consumers.get(sourceEndpointURI));
    }

    @Override
    public void onFlowRegistered(Flow flow) {
        final Consumer consumer = flow.getConsumer();
        // If the consumer belongs to a logic (internal) source
        final Endpoint endpoint = consumer.getEndpoint();
        if(DefaultEndpoint.class.equals(endpoint.getClass())){
            final EndpointURL endpointURL = EndpointURL.parse(flow.getEndpointURI());
            consumers.put(endpointURL.getResource(), consumer);
        }
    }
}
