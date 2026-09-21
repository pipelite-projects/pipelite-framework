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
package io.pipelite.spi.inbox;

/**
 * Resolves the one {@link DurableInbox} instance dedicated to a given flow, creating it on first
 * request and reusing it afterward. Called exactly once per flow, at flow-build time (see {@code
 * FlowFactory.createFlow}) — the mapping from flow to inbox is decided at bootstrap, not threaded
 * through every {@link DurableInbox#enqueue} call, which is why {@code enqueue} itself takes no
 * flow-identifying parameter.
 * <p>
 * Keyed by the flow's name, never by the resource of its source (issue #108): a resource is an
 * address, not an identity, and several flows can legitimately share one - {@code http://orders},
 * an internal {@code orders}, two flows reading the same Kafka topic - while a flow name is unique
 * in a context.
 * <p>
 * Not a third-party extension point — see {@link DurableInbox}'s own Javadoc.
 */
public sealed interface DurableInboxProvider permits SegmentedLogDurableInboxProvider {

    DurableInbox forFlow(String flowName);

}
