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
import io.pipelite.core.context.ChannelAdapterManager;
import io.pipelite.core.context.EndpointFactory;
import io.pipelite.core.definition.TypedSourceDefinition;
import io.pipelite.dsl.definition.EndpointDefinition;
import io.pipelite.expression.support.ReflectionUtils;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelURL;
import io.pipelite.spi.endpoint.DefaultEndpoint;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class DefaultEndpointFactory implements EndpointFactory {

    private final ChannelAdapterManager channelAdapterManager;

    public DefaultEndpointFactory(ChannelAdapterManager channelAdapterManager) {
        Preconditions.notNull(channelAdapterManager, "componentManager is required and cannot be null");
        this.channelAdapterManager = channelAdapterManager;
    }

    @Override
    public Endpoint createEndpoint(EndpointDefinition endpointDefinition) {

        if(endpointDefinition instanceof TypedSourceDefinition){
            final TypedSourceDefinition typedSourceDefinition = (TypedSourceDefinition) endpointDefinition;
            return createTypedSourceEndpoint(typedSourceDefinition);
        }

        final ChannelURL channelURL = ChannelURL.parse(endpointDefinition.getUrl());
        if(channelURL.hasProtocol()){
            final ChannelAdapter channel = channelAdapterManager.resolveChannel(channelURL.getProtocol());
            return channel.createEndpoint(channelURL.getEndpointURL());
        }
        return new DefaultEndpoint(EndpointURL.parse(channelURL.getEndpointURL()));
    }

    private Endpoint createTypedSourceEndpoint(TypedSourceDefinition typedSourceDefinition){

        final Class<? extends Endpoint> endpointType = typedSourceDefinition.getEndpointType();
        try {
            final Constructor<? extends Endpoint> ctr = endpointType.getConstructor(EndpointURL.class);
            EndpointURL endpointURL = EndpointURL.parse(typedSourceDefinition.getUrl());
            return ctr.newInstance(endpointURL);
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException e) {
            ReflectionUtils.rethrowRuntimeException(e);
            return null;
        }
    }

}
