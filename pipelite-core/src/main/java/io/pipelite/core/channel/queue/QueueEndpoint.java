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
package io.pipelite.core.channel.queue;

import io.pipelite.core.source.internal.ConsumerEndpoint;
import io.pipelite.common.support.Preconditions;
import io.pipelite.core.consumer.internal.Consumer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.core.consumer.internal.EventDrivenConsumer;
import io.pipelite.core.consumer.internal.EventDrivenConsumerService;
import io.pipelite.spi.endpoint.Producer;

/**
 * The endpoint of a queue, on both sides. Its consumer is the framework's own event-driven one: nothing is
 * received from outside, the producer of another flow hands the exchanges over ({@link QueueProducer}).
 */
public class QueueEndpoint implements Endpoint, ConsumerEndpoint {

    private final EndpointURL endpointURL;
    private final QueueChannelAdapter channel;

    public QueueEndpoint(EndpointURL endpointURL, QueueChannelAdapter channel) {
        this.endpointURL = Preconditions.notNull(endpointURL, "endpointURL is required and cannot be null");
        this.channel = Preconditions.notNull(channel, "channel is required and cannot be null");
    }

    @Override
    public EndpointURL getEndpointURL() {
        return endpointURL;
    }

    @Override
    public Consumer createConsumer() {
        return new EventDrivenConsumerService(new EventDrivenConsumer(this));
    }

    @Override
    public Producer createProducer() {
        return new QueueProducer(channel, endpointURL);
    }
}
