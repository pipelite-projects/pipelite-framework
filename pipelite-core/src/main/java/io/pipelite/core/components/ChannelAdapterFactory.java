package io.pipelite.core.components;

import io.pipelite.spi.channel.ChannelAdapter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ChannelAdapterFactory {

    public ChannelAdapter instantiateAdapter(Class<? extends ChannelAdapter> componentType){
        try{
            final Constructor<?> componentCtr = componentType.getConstructor();
            return (ChannelAdapter) componentCtr.newInstance();
        } catch (InvocationTargetException | InstantiationException
                 | IllegalAccessException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
