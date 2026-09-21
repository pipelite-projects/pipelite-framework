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
 * Internal source uniqueness (issue #101): an internal source name is an address - {@code
 * link://orders} has to reach exactly one flow - so two flows must not declare the same one.
 * Otherwise the flow registry (the first registered wins) and the link adapter (the last wins)
 * disagree about which flow it is, and the other one is unreachable through the link.
 * <p>
 * Only internal sources, the ones without a protocol. A source resource shared with anything else
 * is not a conflict: {@code http://orders}, an internal {@code orders} and two flows reading the
 * same Kafka topic are all legitimate, because what belongs to a flow (its inbox, the resumption
 * of its retries) is keyed by the flow, not by the resource (issue #108).
 */
public final class InternalSourceUniquenessValidator implements ContextValidator {

    @Override
    public void validate(ValidationContext context, ValidationReport report) {

        final Map<String, String> declaredBy = new HashMap<>();

        for (FlowDefinition flow : context.flowDefinitions()) {
            final Optional<String> name = InternalSources.nameOf(flow, context);
            if (name.isEmpty()) {
                continue;
            }
            final String first = declaredBy.putIfAbsent(name.get(), flow.getFlowName());
            if (first != null) {
                report.error(flow.getFlowName(), String.format(
                    "fromSource(\"%s\"): source name '%s' is already declared by flow '%s'; link://%s must reach exactly one flow",
                    name.get(), name.get(), first, name.get()));
            }
        }
    }

}
