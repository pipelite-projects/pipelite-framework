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

import io.pipelite.channels.kafka.config.DefaultKafkaChannelConfiguration;
import io.pipelite.channels.kafka.config.KafkaChannelConfiguration;
import io.pipelite.channels.kafka.config.KafkaChannelConfigurer;
import io.pipelite.dsl.definition.SourceConfigurer;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelConfigurer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;


public class KafkaChannelAdapter implements ChannelAdapter {

    private final KafkaChannelConfiguration configuration;

    public KafkaChannelAdapter(){
        configuration = new DefaultKafkaChannelConfiguration();
    }

    @Override
    public void configure(ChannelConfigurer<?> channelConfigurer) {
        ((KafkaChannelConfigurer) channelConfigurer).configure(configuration);
    }

    @Override
    public Class<? extends ChannelConfigurer<?>> getChannelConfigurerType() {
        return KafkaChannelConfigurer.class;
    }

    @Override
    public Endpoint createEndpoint(String url) {
        return new KafkaEndpoint(EndpointURL.parse(url), configuration);
    }

    @Override
    public SourceConfigurer newSourceConfigurer() {
        return new KafkaSourceConfigurer();
    }
}
