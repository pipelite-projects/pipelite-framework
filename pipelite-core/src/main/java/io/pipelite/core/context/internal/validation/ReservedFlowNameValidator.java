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
package io.pipelite.core.context.internal.validation;

import io.pipelite.core.context.internal.ReservedNames;
import io.pipelite.dsl.definition.FlowDefinition;

import java.util.Optional;

/**
 * Fail fast on a name the framework reserves for one of its own internal flows (issue #116),
 * instead of letting it collide in silence with {@code flowRegistry}'s bookkeeping. Only {@code
 * retry-channel} is reserved today - see {@link ReservedNames}'s own Javadoc for why {@code
 * withErrorChannel(...)}/{@code toDLQ()} have no equivalent to protect.
 * <p>
 * Checked on both axes a collision can happen on: the flow's own {@code defineFlow(...)} name, an
 * identity, and the queue its source reads, an address (issue #111) - two different things that
 * {@code flowRegistry} keys two different maps by by, {@code putIfAbsent}, first registered wins.
 */
public final class ReservedFlowNameValidator implements ContextValidator {

    @Override
    public void validate(ValidationContext context, ValidationReport report) {
        for (FlowDefinition flow : context.flowDefinitions()) {

            if (ReservedNames.RETRY_CHANNEL.equals(flow.getFlowName())) {
                report.error(flow.getFlowName(), String.format(
                    "defineFlow(\"%s\"): this name is reserved for the framework's own retry channel; choose a different flow name",
                    ReservedNames.RETRY_CHANNEL));
            }

            final Optional<String> queueName = QueueSources.nameOf(flow, context);
            if (queueName.isPresent() && ReservedNames.RETRY_CHANNEL.equals(queueName.get())) {
                report.error(flow.getFlowName(), String.format(
                    "fromSource(\"queue://%s\"): '%s' is reserved for the framework's own retry channel; choose a different queue name",
                    ReservedNames.RETRY_CHANNEL, ReservedNames.RETRY_CHANNEL));
            }
        }
    }

}
