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

import io.pipelite.dsl.definition.FlowDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Queue uniqueness (issues #101, #111): a queue is read by exactly one flow, with as many
 * consumers as its {@code concurrency} says, so two flows must not declare the same one. Two flows
 * would be two different logics on one queue, not two consumers of one; and the flow registry (the
 * first registered wins) and the queue adapter (the last wins) would disagree about which flow
 * {@code queue://orders} reaches, leaving the other unreachable through it.
 * <p>
 * Only queues. A source resource shared with anything else is not a conflict: {@code
 * http://orders}, {@code queue://orders} and two flows reading the same Kafka topic are all
 * legitimate, because what belongs to a flow (its inbox, the resumption of its retries) is keyed
 * by the flow, not by the resource (issue #108).
 */
public final class QueueSourceUniquenessValidator implements ContextValidator {

    @Override
    public void validate(ValidationContext context, ValidationReport report) {

        final Map<String, String> declaredBy = new HashMap<>();

        for (FlowDefinition flow : context.flowDefinitions()) {
            final Optional<String> name = QueueSources.nameOf(flow, context);
            if (name.isEmpty()) {
                continue;
            }
            final String first = declaredBy.putIfAbsent(name.get(), flow.getFlowName());
            if (first != null) {
                report.error(flow.getFlowName(), String.format(
                    "fromSource(\"queue://%s\"): queue '%s' is already read by flow '%s'; scale it with concurrency instead of declaring a second flow",
                    name.get(), name.get(), first));
            }
        }
    }

}
