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
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.inbox.DurableInboxProvider;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

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
     * flow is addressed as {@code link://<source endpoint name>} - the name its own
     * {@code fromSource("<source endpoint name>")} declares, without protocol - and an external
     * system through its channel adapter's protocol ({@code kafka://...}). A bare name is not a
     * destination and is rejected: it would be ambiguous between a source endpoint name and a
     * flow name.
     *
     * @throws IllegalArgumentException if {@code destinationURL} is not a URL, or its protocol has
     *                                  no registered channel adapter.
     */
    void supplyExchange(String destinationURL, ExchangeImpl exchange);

    /**
     * Looks up a registered {@link Flow} by its source endpoint resource (the same value
     * {@code Flow#getEndpointURI()} was registered under). Used to resume a flow's execution
     * directly at a specific {@code FlowNode} (see {@code SupplyExchangeProcessor}) rather than
     * re-entering from the source — the registry backing this lookup is otherwise entirely
     * internal to this context's implementation.
     */
    Optional<Flow> tryFindFlow(String sourceEndpointResource);

    /**
     * Looks up a registered {@link Flow} by its own name — the value passed to
     * {@code Pipelite.defineFlow(String flowName)} — not by its {@code fromSource(...)} resource
     * (see {@link #tryFindFlow(String)} for that). The flow name is an identity, not an address:
     * since issue #102 no DSL method takes it as a destination (see {@link #supplyExchange}).
     */
    Optional<Flow> tryFindFlowByName(String flowName);

    EndpointFactory getEndpointFactory();

    ExchangeFactory getExchangeFactory();

    AggregateRepository getAggregateRepository();

    /**
     * Resolves the per-source {@code DurableInbox} a consumer durably records its intake with
     * (issue #70) — called once per source at flow-build time (see {@code FlowFactory}), never
     * on the hot path.
     */
    DurableInboxProvider getDurableInboxProvider();

    /**
     * The shared, application-wide worker pool used by internal (no-protocol) {@code fromSource}
     * endpoints when {@code concurrency > 1} is configured on their URL. A single pool sized
     * independently of how many flows use it or how high each sets its own {@code concurrency} —
     * see {@link io.pipelite.core.context.ConfigurablePipeliteContext#setMaxSourceWorkerPoolSize(int)}.
     * Only meaningful after {@link #start()}.
     */
    ExecutorService getSourceWorkerPool();

    void start();

    void stop();

}
