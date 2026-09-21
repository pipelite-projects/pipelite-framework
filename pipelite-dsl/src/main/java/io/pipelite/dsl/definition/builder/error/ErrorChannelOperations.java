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
     * from {@code definedFlow(String)}. The target is a URL, like every destination in the DSL (issue
     * #102): {@code queue://<queue name>} for an internal flow - the queue its own {@code
     * fromSource("queue://<queue name>")} reads, never the flow's {@code defineFlow(...)} name -
     * or a registered channel adapter's protocol ({@code kafka://...}) for an external system,
     * delivered directly to that adapter's {@code Producer} with no {@code Flow} required to
     * receive it. A bare name is rejected when the flow is defined.
     */
    ChannelErrorChannelOperations toChannel(String target);

    /**
     * Declares the framework's built-in, ready-to-use dead letter queue as the target - no
     * {@code Flow} and no channel adapter required (issue #93).
     */
    DeadLetterQueueErrorChannelOperations toDLQ();

}
