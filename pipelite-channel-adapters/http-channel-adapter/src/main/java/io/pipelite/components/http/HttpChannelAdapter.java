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

import io.pipelite.components.http.config.DefaultHttpChannelConfiguration;
import io.pipelite.components.http.config.HttpChannelConfiguration;
import io.pipelite.components.http.config.HttpChannelConfigurer;
import io.pipelite.components.http.undertow.DefaultHttpHandler;
import io.pipelite.dsl.definition.SourceConfigurer;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelConfigurer;
import io.pipelite.spi.context.ContextEventListener;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.PathHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class HttpChannelAdapter implements ChannelAdapter, ContextEventListener {

    private final Map<String, HttpSource> sourcesByResource;

    private final HttpChannelConfiguration configuration;

    private Undertow server;

    public HttpChannelAdapter(){
        sourcesByResource = new LinkedHashMap<>();
        configuration = new DefaultHttpChannelConfiguration();
    }

    /**
     * Adapter-wide: port, host and the request body size cap (issues #48, #52), supplied by the
     * caller via {@code context.addChannelConfigurer((HttpChannelConfigurer) c -> ...)} - the same
     * mechanism {@code KafkaChannelAdapter} uses for {@code bootstrapServers}.
     */
    @Override
    public void configure(ChannelConfigurer<?> channelConfigurer) {
        ((HttpChannelConfigurer) channelConfigurer).configure(configuration);
    }

    @Override
    public Class<? extends ChannelConfigurer<?>> getChannelConfigurerType() {
        return HttpChannelConfigurer.class;
    }

    @Override
    public Endpoint createEndpoint(String url) {
        final EndpointURL endpointURL = EndpointURL.parse(url);
        return new HttpEndpoint(endpointURL, this);
    }

    @Override
    public SourceConfigurer newSourceConfigurer() {
        return new HttpSourceConfigurer();
    }

    @Override
    public void onContextStarted() {

        // Start Undertow only if a source was registered
        if(sourcesByResource.isEmpty()){
            return;
        }

        if(server == null){
            server = Undertow.builder()
                .addHttpListener(configuration.getPort(), configuration.getHost())
                .setServerOption(UndertowOptions.MAX_ENTITY_SIZE, configuration.getMaxEntitySize())
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

    public void registerSource(String resource, HttpSource source){
        if(sourcesByResource.containsKey(resource)){
            throw new IllegalStateException(String.format("Consumer resource '%s' already added", resource));
        }
        sourcesByResource.put(resource, source);
    }

    public Optional<HttpSource> tryResolveSource(String resource){
        return Optional.ofNullable(sourcesByResource.get(resource));
    }

    private HttpHandler createHttpHandler(){
        final PathHandler pathHandler = Handlers.path();
        final DefaultHttpHandler httpHandler = new DefaultHttpHandler(this);
        sourcesByResource.keySet().forEach(resource -> {
            pathHandler.addExactPath(resource, httpHandler);
        });
        return new BlockingHandler(pathHandler);
    }

}
