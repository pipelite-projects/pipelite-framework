package io.pipelite.core.context;

import io.pipelite.dsl.definition.EndpointDefinition;
import io.pipelite.spi.endpoint.Endpoint;

public interface EndpointFactory {

    Endpoint createEndpoint(EndpointDefinition endpointDefinition);

}
