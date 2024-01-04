/*
 * Copyright (C) 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
