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

import io.pipelite.channels.kafka.config.KafkaChannelConfigurationImpl;
import io.pipelite.channels.kafka.config.KafkaChannelConfiguration;
import io.pipelite.channels.kafka.config.KafkaChannelConfigurer;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelConfigurer;
import io.pipelite.spi.context.ContextEventListener;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;

import java.util.ArrayList;
import java.util.Collection;

public class KafkaChannelAdapter implements ChannelAdapter, ExchangeFactoryAware, ContextEventListener {

    private final KafkaChannelConfiguration configuration;

    private final Collection<KafkaEndpoint> endpoints;

    private ExchangeFactory exchangeFactory;

    public KafkaChannelAdapter(){
        configuration = new KafkaChannelConfigurationImpl();
        endpoints = new ArrayList<>();
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
        final KafkaEndpoint endpoint = new KafkaEndpoint(EndpointURL.parse(url), this, configuration);
        endpoints.add(endpoint);
        return endpoint;
    }

    @Override
    public void onContextStarted() {
        endpoints.forEach(KafkaEndpoint::onContextStarted);
    }

    @Override
    public void onContextStopped() {
        endpoints.forEach(KafkaEndpoint::onContextStopped);
    }

    @Override
    public void onFlowRegistered(Flow flow) {
        endpoints.forEach(endpoint -> endpoint.onFlowRegistered(flow));
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }
}
