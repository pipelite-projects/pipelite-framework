package io.pipelite.core.context;

import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelConfigurer;
import io.pipelite.spi.flow.Flow;

import java.util.Optional;

public interface ChannelAdapterManager {

    void scan();

    void registerChannelAdapter(String channelName, ChannelAdapter channel);

    void addChannelConfigurer(ChannelConfigurer<?> channelConfigurer);

    ChannelAdapter resolveChannel(String channelName);
    Optional<ChannelAdapter> tryResolveChannel(String channelName);

    void notifyFlowRegisterd(Flow flow);
    void notifyContextStarted();
    void notifyContextStopped();

}
