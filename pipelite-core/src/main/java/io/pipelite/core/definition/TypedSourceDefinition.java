package io.pipelite.core.definition;

import io.pipelite.dsl.definition.SourceDefinition;
import io.pipelite.spi.endpoint.Endpoint;

public interface TypedSourceDefinition extends SourceDefinition {

    Class<? extends Endpoint> getEndpointType();

}
