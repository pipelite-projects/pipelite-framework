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
package io.pipelite.dsl.definition.builder.error;

public interface ErrorChannelOperations {

    /**
     * Declares the internal flow that dead-lettered exchanges are routed to. {@code flowName} is
     * the target flow's own name — the exact value passed to that flow's own
     * {@code Pipelite.defineFlow(String flowName)} — not a URL, not a protocol, and not
     * necessarily that flow's {@code fromSource(...)} resource (the two may differ; resolution is
     * always by flow identity, via {@code PipeliteContext.tryFindFlowByName(...)}). A direct
     * channel adapter (Kafka, HTTP, ...) is never a valid target — there's no reason to bypass
     * Pipelite's own flow abstraction for this, since the target flow can itself forward wherever
     * it needs to (external system, audit log, ...) once it receives the exchange. The rejection
     * of any protocol-qualified value is enforced by the implementation, not just documented —
     * see {@code ErrorChannelBuilder}.
     */
    DefinedErrorChannelOperations definedFlow(String flowName);

}
