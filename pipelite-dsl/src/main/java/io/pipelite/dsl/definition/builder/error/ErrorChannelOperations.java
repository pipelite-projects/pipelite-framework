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
     * Declares where a dead-lettered exchange is routed to (issue #91) - renamed and broadened
     * from {@code definedFlow(String)}. Accepts either:
     * <ul>
     *     <li>a bare name - the target flow's own name, the exact value passed to that flow's own
     *     {@code Pipelite.defineFlow(String flowName)}, not necessarily its {@code fromSource(...)}
     *     resource; resolved via {@code PipeliteContext.tryFindFlowByName(...)}, exactly as
     *     {@code definedFlow(...)} always did;</li>
     *     <li>a protocol-qualified URL (e.g. {@code link://...}, {@code kafka://...}) - delivered
     *     directly to that channel adapter's {@code Producer}, with no {@code Flow} required to
     *     receive it. Resolved the same way {@code PipeliteContext.supplyExchange(...)} already
     *     resolves its own protocol branch.</li>
     * </ul>
     */
    ChannelErrorChannelOperations toChannel(String target);

    /**
     * Declares the framework's built-in, ready-to-use dead letter queue as the target - no
     * {@code Flow} and no channel adapter required (issue #93).
     */
    DeadLetterQueueErrorChannelOperations toDLQ();

}
