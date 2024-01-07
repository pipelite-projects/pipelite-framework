package io.pipelite.core.components;

import io.pipelite.spi.channel.ChannelAdapter;

import java.util.Objects;

public class CandidateComponentMetadata {

    private final String protocolName;
    private final Class<? extends ChannelAdapter> channelAdapterType;

    public CandidateComponentMetadata(String protocolName, Class<? extends ChannelAdapter> channelAdapterType) {
        this.protocolName = protocolName;
        this.channelAdapterType = channelAdapterType;
    }

    public String getProtocolName() {
        return protocolName;
    }

    public Class<? extends ChannelAdapter> getChannelAdapterType() {
        return channelAdapterType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CandidateComponentMetadata that = (CandidateComponentMetadata) o;
        return Objects.equals(channelAdapterType, that.channelAdapterType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelAdapterType);
    }

}
