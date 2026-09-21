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
package io.pipelite.components.queue;

import io.pipelite.dsl.definition.SourceConfigurer;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.context.ContextEventListener;
import io.pipelite.spi.endpoint.*;
import io.pipelite.spi.flow.Flow;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The queue in front of an internal flow (issue #111). {@code fromSource("queue://x")} declares the
 * queue named {@code x}, read by that flow and by its concurrent consumers; {@code toSink("queue://x")}
 * and the other destinations put an exchange on it. One endpoint type serves both sides, so what
 * makes a source a queue is its protocol, like any other channel adapter.
 */
public class QueueChannelAdapter implements ChannelAdapter, ContextEventListener {

    private final Map<String, Consumer> consumers;

    public QueueChannelAdapter() {
        this.consumers = new HashMap<>();
    }

    @Override
    public Endpoint createEndpoint(String url) {
        return new QueueEndpoint(EndpointURL.parse(url), this);
    }

    /**
     * The queue's own concurrency settings ({@code concurrency}, {@code executorType}): the
     * consumers of a queue are the consumers of the one flow that reads it. No other adapter has
     * them.
     */
    @Override
    public SourceConfigurer newSourceConfigurer() {
        return new SourceConcurrencyConfigurer();
    }

    public Optional<Consumer> tryResolveConsumer(String queueName){
        return Optional.ofNullable(consumers.get(queueName));
    }

    @Override
    public void onFlowRegistered(Flow flow) {
        final Consumer consumer = flow.getConsumer();
        // Only a flow whose source is a queue: any other source has a protocol of its own
        final Endpoint endpoint = consumer.getEndpoint();
        if(endpoint instanceof QueueEndpoint){
            consumers.put(endpoint.getEndpointURL().getResource(), consumer);
        }
    }
}
