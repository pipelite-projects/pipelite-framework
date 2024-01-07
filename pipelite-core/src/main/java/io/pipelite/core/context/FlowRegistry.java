package io.pipelite.core.context;

import io.pipelite.spi.flow.Flow;

import java.util.Optional;

public interface FlowRegistry {

    boolean isRegistered(String sourceEndpointURI);
    Optional<Flow> tryFindFlow(String sourceEndpointURI);
    void addFlow(Flow flow);

}
