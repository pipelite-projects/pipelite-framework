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
package io.pipelite.components.http;

import io.pipelite.components.http.config.HttpChannelConfiguration;
import io.pipelite.components.http.config.HttpChannelConfigurationImpl;
import io.pipelite.components.http.config.HttpChannelConfigurer;
import io.pipelite.components.http.undertow.DefaultHttpHandler;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelConfigurer;
import io.pipelite.spi.context.ContextEventListener;
import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.PathHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class HttpChannelAdapter implements ChannelAdapter, ExchangeFactoryAware, ContextEventListener {

    private static final Integer DEFAULT_SERVER_PORT = 80;

    private final HttpChannelConfiguration configuration;

    private final Map<String, Consumer> consumersByResource;

    private Undertow server;

    private ExchangeFactory exchangeFactory;

    public HttpChannelAdapter(){
        configuration = new HttpChannelConfigurationImpl();
        consumersByResource = new LinkedHashMap<>();
    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

    @Override
    public Endpoint createEndpoint(String url) {
        final EndpointURL endpointURL = EndpointURL.parse(url);
        return new HttpEndpoint(endpointURL, this);
    }

    @Override
    public void configure(ChannelConfigurer<?> channelConfigurer) {
        ((HttpChannelConfigurer) channelConfigurer).configure(configuration);
    }

    @Override
    public Class<? extends ChannelConfigurer<?>> getChannelConfigurerType() {
        return HttpChannelConfigurer.class;
    }

    @Override
    public void onContextStarted() {

        // Start Undertow only if was registered consumers
        if(consumersByResource.isEmpty()){
            return;
        }

        if(server == null){
            server = Undertow.builder()
                .addHttpListener(DEFAULT_SERVER_PORT, "0.0.0.0")
                .setHandler(createHttpHandler())
                .build();
        }
        server.start();
    }

    @Override
    public void onContextStopped() {
        if(server != null){
            server.stop();
        }
    }

    public void registerConsumer(String resource, Consumer consumer){
        if(consumersByResource.containsKey(resource)){
            throw new IllegalStateException(String.format("Consumer resource '%s' already added", resource));
        }
        consumersByResource.put(resource, consumer);
    }

    public Optional<Consumer> tryResolveConsumer(String resource){
        if(consumersByResource.containsKey(resource)){
            return Optional.of(consumersByResource.get(resource));
        }
        return Optional.empty();
    }

    private HttpHandler createHttpHandler(){
        final PathHandler pathHandler = Handlers.path();
        final DefaultHttpHandler httpHandler = new DefaultHttpHandler(this, configuration);
        httpHandler.setExchangeFactory(exchangeFactory);

        consumersByResource.keySet().forEach(resource ->
            pathHandler.addExactPath(resource, httpHandler));

        return new BlockingHandler(pathHandler);
    }

}
