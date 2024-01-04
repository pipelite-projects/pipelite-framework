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
package io.pipelite.channels.kafka;

import io.pipelite.channels.kafka.config.KafkaChannelConfiguration;
import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.context.ContextEventListener;
import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.endpoint.DefaultEndpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;

public class KafkaEndpoint extends DefaultEndpoint implements ContextEventListener {

    private final KafkaChannelConfiguration configuration;

    private DefaultKafkaProducer producer;

    public KafkaEndpoint(EndpointURL endpointURL, KafkaChannelAdapter channel, KafkaChannelConfiguration configuration) {
        super(endpointURL, channel);
        Preconditions.notNull(configuration, "configuration is required and cannot be null");
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer() {
        return new KafkaConsumerService(configuration, this);
    }

    @Override
    public Producer createProducer() {
        producer = new DefaultKafkaProducer(this, configuration);
        return producer;
    }

    @Override
    public void onContextStarted() {
        if(producer != null){
            producer.onContextStarted();
        }
    }

    @Override
    public void onContextStopped() {
        if(producer != null){
            producer.onContextStopped();
        }
    }
}
