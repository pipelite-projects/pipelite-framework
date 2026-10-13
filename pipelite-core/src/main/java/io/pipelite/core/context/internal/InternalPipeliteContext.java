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
package io.pipelite.core.context.internal;

import io.pipelite.core.context.EndpointFactory;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.flow.barrier.BarrierRepository;
import io.pipelite.core.flow.split.AggregateRepository;
import io.pipelite.core.flow.internal.Flow;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.flow.execution.inbox.DurableInboxProvider;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

/**
 * What the nodes and the factories of the framework need from a context, and the code of a flow must not
 * see: the registry of the flows, the factories, the repositories, the worker pool. It was part of {@link
 * PipeliteContext}, the interface applications use, until issue #159.
 */
public interface InternalPipeliteContext extends PipeliteContext {

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

    /** Where the barriers keep the exchanges that wait for the others (issue #150). */
    BarrierRepository getBarrierRepository();

    /**
     * Resolves the per-source {@code DurableInbox} a consumer durably records its intake with
     * (issue #70) — called once per source at flow-build time (see {@code FlowFactory}), never
     * on the hot path.
     */
    DurableInboxProvider getDurableInboxProvider();

    /**
     * The shared, application-wide worker pool used by {@code fromSource("queue://...")}
     * endpoints when {@code concurrency > 1} is configured on their URL. A single pool sized
     * independently of how many flows use it or how high each sets its own {@code concurrency} —
     * see {@link io.pipelite.core.context.ConfigurablePipeliteContext#setMaxSourceWorkerPoolSize(int)}.
     * Only meaningful after {@link #start()}.
     */
    ExecutorService getSourceWorkerPool();
}
