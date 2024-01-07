package io.pipelite.core.context.impl;

import io.pipelite.core.context.FlowRegistry;
import io.pipelite.spi.flow.Flow;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultFlowRegistry implements FlowRegistry {

    private final Map<String, Flow> flows;

    public DefaultFlowRegistry() {
        this.flows = new ConcurrentHashMap<>();
    }

    @Override
    public boolean isRegistered(String sourceEndpointURI) {
        return flows.containsKey(sourceEndpointURI);
    }

    @Override
    public Optional<Flow> tryFindFlow(String sourceEndpointURI) {
        return Optional.ofNullable(flows.get(sourceEndpointURI));
    }

    @Override
    public void addFlow(Flow flow) {
        Objects.requireNonNull(flow, "flow is required and cannot be null");
        final String sourceEndpointURI = flow.getEndpointURI();
        flows.putIfAbsent(sourceEndpointURI, flow);
    }
}
