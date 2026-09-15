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
 * Resolves the one {@link DurableInbox} instance dedicated to a given source resource, creating
 * it on first request and reusing it afterward. Called exactly once per source, at flow-build
 * time (see {@code FlowFactory.createFlow}) — the mapping from resource to inbox is decided at
 * bootstrap, not threaded through every {@link DurableInbox#enqueue} call, which is why {@code
 * enqueue} itself takes no resource-identifying parameter.
 * <p>
 * Not a third-party extension point — see {@link DurableInbox}'s own Javadoc.
 */
public sealed interface DurableInboxProvider permits SegmentedLogDurableInboxProvider {

    DurableInbox forResource(String resourceKey);

}
