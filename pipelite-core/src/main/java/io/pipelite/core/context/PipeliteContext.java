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
package io.pipelite.core.context;

import io.pipelite.core.flow.split.AggregateRepository;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.channel.ChannelConfigurer;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;

import java.util.Optional;

public interface PipeliteContext {

    /**
     * @throws DuplicateFlowDefinitionException if a flow with the same name is already registered.
     */
    void registerFlowDefinition(FlowDefinition flowDefinition);

    /**
     * Scans {@code configurationClass} (must be annotated {@code @FlowConfiguration}) via the
     * native {@code FlowConfigurationScanner}, resolving {@code @DefineFlow} method parameters
     * against this context's {@code DependencyRegistry}, and registers every resulting
     * {@code FlowDefinition} (see {@link #registerFlowDefinition}).
     */
    void registerFlowConfigurationClass(Class<?> configurationClass);

    /**
     * Registers an instance in this context's native {@code DependencyRegistry}, under the
     * given qualifier name, for resolution of {@code @DefineFlow} method parameters.
     */
    void registerDependency(String name, Object instance);

    /**
     * Looks up a registered {@code FlowDefinition} by name.
     */
    Optional<FlowDefinition> getFlowDefinition(String flowName);

    void addChannelConfigurer(ChannelConfigurer<?> configurer);

    boolean isRegistered(String flowName);

    void supplyExchange(String endpointURL, Exchange exchange);

    EndpointFactory getEndpointFactory();

    ExchangeFactory getExchangeFactory();

    AggregateRepository getAggregateRepository();

    void start();

    void stop();

}
