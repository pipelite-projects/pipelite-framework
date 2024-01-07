package io.pipelite.core.definition;

import io.pipelite.dsl.definition.ErrorChannelDefinition;
import io.pipelite.spi.endpoint.EndpointURL;

public class ErrorChannelDefinitionImpl implements ErrorChannelDefinition {

    private final EndpointURL endpointURL;

    public ErrorChannelDefinitionImpl(String endpointURL) {
        this.endpointURL = EndpointURL.parse(endpointURL);
    }

    @Override
    public String getEndpointURL() {
        return endpointURL.getResource();
    }

    @Override
    public ChannelType getErrorChannelType() {
        return ChannelType.RETRY_CHANNEL;
    }
}
