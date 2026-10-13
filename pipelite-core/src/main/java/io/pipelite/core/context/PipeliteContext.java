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

import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.channel.ChannelConfigurer;

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

    /**
     * Delivers {@code exchange} to {@code destinationURL}: a URL, always (issue #102). An internal
     * flow is addressed as {@code queue://<queue name>} - the queue its own
     * {@code fromSource("queue://<queue name>")} reads - and an external system through its
     * channel adapter's protocol ({@code kafka://...}). A bare name is not a destination and is
     * rejected: it would be ambiguous between a queue name and a flow name.
     *
     * @throws IllegalArgumentException if {@code destinationURL} is not a URL, or its protocol has
     *                                  no registered channel adapter.
     */
    void supplyExchange(String destinationURL, Exchange exchange);

    /**
     * A new exchange whose input payload is {@code payload}, to be delivered with {@link #supplyExchange}.
     * Issue #159: this replaces {@code getExchangeFactory().createExchange(...)}, which exposed the
     * framework's own exchange types to the code of a flow.
     */
    Exchange createExchange(Object payload);

    void start();

    void stop();

}
