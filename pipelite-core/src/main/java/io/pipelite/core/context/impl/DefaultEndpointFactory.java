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
package io.pipelite.core.context.impl;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.config.EndpointURLPropertyResolver;
import io.pipelite.core.context.ChannelAdapterManager;
import io.pipelite.core.context.EndpointFactory;
import io.pipelite.core.context.UnsupportedSourceConcurrencyException;
import io.pipelite.core.definition.TypedSourceDefinition;
import io.pipelite.dsl.definition.EndpointDefinition;
import io.pipelite.expression.support.ReflectionUtils;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelURL;
import io.pipelite.spi.endpoint.DefaultEndpoint;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.concurrent.SourceConcurrencyProperties;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class DefaultEndpointFactory implements EndpointFactory {

    private final ChannelAdapterManager channelAdapterManager;
    private EndpointURLPropertyResolver endpointURLPropertyResolver;

    public DefaultEndpointFactory(ChannelAdapterManager channelAdapterManager, EndpointURLPropertyResolver endpointURLPropertyResolver) {
        Preconditions.notNull(channelAdapterManager, "componentManager is required and cannot be null");
        Preconditions.notNull(endpointURLPropertyResolver, "endpointURLPropertyResolver is required and cannot be null");
        this.channelAdapterManager = channelAdapterManager;
        this.endpointURLPropertyResolver = endpointURLPropertyResolver;
    }

    public void setEndpointURLPropertyResolver(EndpointURLPropertyResolver endpointURLPropertyResolver) {
        Preconditions.notNull(endpointURLPropertyResolver, "endpointURLPropertyResolver is required and cannot be null");
        this.endpointURLPropertyResolver = endpointURLPropertyResolver;
    }

    @Override
    public Endpoint createEndpoint(EndpointDefinition endpointDefinition) {

        if(endpointDefinition instanceof TypedSourceDefinition typedSourceDefinition){
            return createTypedSourceEndpoint(typedSourceDefinition);
        }

        final String resolvedUrl = endpointURLPropertyResolver.resolve(endpointDefinition.getUrl());
        final ChannelURL channelURL = ChannelURL.parse(resolvedUrl);
        if(channelURL.hasProtocol()){
            rejectSourceConcurrencyParams(channelURL.getProtocol(), channelURL.getEndpointURL());
            final ChannelAdapter channel = channelAdapterManager.resolveChannel(channelURL.getProtocol());
            return channel.createEndpoint(channelURL.getEndpointURL());
        }
        return new DefaultEndpoint(EndpointURL.parse(channelURL.getEndpointURL()));
    }

    /**
     * {@code concurrency}/{@code executorType} only apply to no-protocol (internal) sources —
     * see {@link UnsupportedSourceConcurrencyException}. Deliberately does NOT call {@link
     * EndpointURL#parse(String)} (default resource pattern): some adapters (e.g. File) validate
     * their resource against a wider pattern of their own, and re-validating it here with the
     * default pattern would falsely reject otherwise-legitimate URLs. This only ever looks at the
     * raw query string, never the resource portion.
     */
    private static void rejectSourceConcurrencyParams(String protocol, String endpointURL) {
        final int queryIndex = endpointURL.indexOf('?');
        if (queryIndex < 0) {
            return;
        }
        final String query = endpointURL.substring(queryIndex + 1);
        for (String pair : query.split("&")) {
            final String key = pair.split("=", 2)[0];
            if (SourceConcurrencyProperties.CONCURRENCY.equals(key) || SourceConcurrencyProperties.EXECUTOR_TYPE.equals(key)) {
                throw new UnsupportedSourceConcurrencyException(protocol, key);
            }
        }
    }

    private Endpoint createTypedSourceEndpoint(TypedSourceDefinition typedSourceDefinition){

        final Class<? extends Endpoint> endpointType = typedSourceDefinition.getEndpointType();
        try {
            final Constructor<? extends Endpoint> ctr = endpointType.getConstructor(EndpointURL.class);
            final String resolvedUrl = endpointURLPropertyResolver.resolve(typedSourceDefinition.getUrl());
            EndpointURL endpointURL = EndpointURL.parse(resolvedUrl);
            return ctr.newInstance(endpointURL);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            ReflectionUtils.rethrowRuntimeException(e);
            return null;
        }
    }

}
