package io.pipelite.core.definition;

import io.pipelite.spi.endpoint.Endpoint;

public class TypedSourceDefinitionImpl extends SourceDefinitionImpl implements TypedSourceDefinition {

    private final Class<? extends Endpoint> endpointType;

    public TypedSourceDefinitionImpl(String url, Class<? extends Endpoint> endpointType) {
        super(url);
        this.endpointType = endpointType;
    }

    @Override
    public Class<? extends Endpoint> getEndpointType() {
        return endpointType;
    }
}
