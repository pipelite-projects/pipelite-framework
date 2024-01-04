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

import io.pipelite.dsl.Headers;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;

import java.util.Objects;

public class DefaultEndpoint implements Endpoint, ExchangeFactoryAware {

    private final EndpointURL endpointURL;
    private final ChannelAdapter channel;
    protected ExchangeFactory exchangeFactory;

    public DefaultEndpoint(EndpointURL endpointURL){
        this(endpointURL, null);
    }

    public DefaultEndpoint(EndpointURL endpointURL, ChannelAdapter channel) {
        Objects.requireNonNull(endpointURL, "endpointURL is required and cannot be null");
        this.endpointURL = endpointURL;
        this.channel = channel;
        //isSink = false;
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

    @Override
    public EndpointURL getEndpointURL() {
        return endpointURL;
    }

    @Override
    public EndpointProperties getProperties() {
        return endpointURL.getProperties();
    }

    @Override
    public Consumer createConsumer() {
        return new EventDrivenConsumerService(new EventDrivenConsumer(this));
    }

    @Override
    public Producer createProducer() {
        return new DefaultProducer(this);
    }

    @Override
    public <T extends ChannelAdapter> T getChannelAdapter(Class<T> channelType) {
        Objects.requireNonNull(channelType, "componentType is required and cannot be null");
        if(channel == null){
            return null;
        }
        if(channelType.isAssignableFrom(channel.getClass())) {
            return channelType.cast(channel);
        }
        throw new ClassCastException(String.format("Unable to cast from %s to expectedType %s", channel.getClass(), channelType));
    }

    @Override
    public Exchange createExchange() {
        assert exchangeFactory != null : "ExchangeFactory is required and cannot be null.";
        return exchangeFactory.createExchange();
    }

    @Override
    public Exchange createExchange(Headers headers) {
        assert exchangeFactory != null : "ExchangeFactory is required and cannot be null.";
        return exchangeFactory.createExchange(headers);
    }

    @Override
    public boolean isSink() {
        return false;
    }

    @Override
    public void setSink() {
    }
}
