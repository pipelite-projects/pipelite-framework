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
