package io.pipelite.dsl.definition;

public interface ErrorChannelDefinition {

    enum ChannelType {
        RETRY_CHANNEL, DEFINED_CHANNEL;
    }

    String getEndpointURL();

    ChannelType getErrorChannelType();

}
