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

import io.pipelite.core.context.internal.ChannelURL;
import io.pipelite.core.definition.builder.barrier.BarrierSpec;
import io.pipelite.core.flow.barrier.BarrierNodeFactory;
import io.pipelite.core.flow.internal.FlowNode;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.definition.ProcessorDefinition;
import io.pipelite.spi.endpoint.EndpointURL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks, when the context starts, what a barrier awaits (issue #150): that a flow reads the queue it names, that
 * no flow waits for its own completion, and that flows do not wait for each other in a cycle. The first two are a
 * mistake in the URL; the third could never be released, so the barriers would only wait for their timeout.
 */
public final class BarrierReferenceValidator implements ContextValidator {

    private record Await(String barrierName, String queueName, String flowURL) {
    }

    @Override
    public void validate(ValidationContext context, ValidationReport report) {

        final Map<String, String> flowByQueue = new HashMap<>();
        for (FlowDefinition flow : context.flowDefinitions()) {
            QueueSources.nameOf(flow, context).ifPresent(queue -> flowByQueue.putIfAbsent(queue, flow.getFlowName()));
        }

        // for each flow, the flows it waits for
        final Map<String, Set<String>> waitsFor = new LinkedHashMap<>();
        for (FlowDefinition flow : context.flowDefinitions()) {
            for (Await await : awaitsOf(flow, context, report)) {
                final String awaited = flowByQueue.get(await.queueName());
                if (awaited == null) {
                    report.error(flow.getFlowName(), String.format(
                        "barrier '%s' awaits \"%s\", but no flow reads the queue '%s'",
                        await.barrierName(), await.flowURL(), await.queueName()));
                } else if (awaited.equals(flow.getFlowName())) {
                    report.error(flow.getFlowName(), String.format(
                        "barrier '%s' awaits \"%s\", which is the flow that declares it: it would wait for its own completion",
                        await.barrierName(), await.flowURL()));
                } else {
                    waitsFor.computeIfAbsent(flow.getFlowName(), name -> new LinkedHashSet<>()).add(awaited);
                }
            }
        }

        final List<String> cycle = findCycle(waitsFor);
        if (!cycle.isEmpty()) {
            report.error(cycle.get(0), "the flows wait for each other in a cycle, so none of them could ever go on: "
                + String.join(" -> ", cycle));
        }
    }

    private List<Await> awaitsOf(FlowDefinition flow, ValidationContext context, ValidationReport report) {
        final List<Await> awaits = new ArrayList<>();
        final Iterator<ProcessorDefinition> processors = flow.iterateProcessorDefinitions();
        while (processors.hasNext()) {
            final ProcessorDefinition definition = processors.next();
            final FlowNode node = definition.getProcessor(FlowNode.class);
            final Optional<BarrierSpec> spec = node == null ? Optional.empty() : BarrierNodeFactory.specOf(node);
            if (spec.isEmpty()) {
                continue;
            }
            for (BarrierSpec.Await await : spec.get().getAwaits()) {
                try {
                    final ChannelURL channelURL = ChannelURL.parse(context.resolveURL(await.flowURL()));
                    awaits.add(new Await(definition.getProcessorName(),
                        EndpointURL.parse(channelURL.getEndpointURL()).getResource(), await.flowURL()));
                } catch (RuntimeException unresolvable) {
                    report.error(flow.getFlowName(), String.format("barrier '%s' awaits \"%s\", which cannot be read: %s",
                        definition.getProcessorName(), await.flowURL(), unresolvable.getMessage()));
                }
            }
        }
        return awaits;
    }

    /** One cycle of the graph, as the flows along it with the first repeated at the end, or an empty list. */
    private static List<String> findCycle(Map<String, Set<String>> waitsFor) {
        final Set<String> done = new HashSet<>();
        for (String start : waitsFor.keySet()) {
            final List<String> path = new ArrayList<>();
            final List<String> cycle = visit(start, waitsFor, new HashSet<>(), done, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        return List.of();
    }

    private static List<String> visit(String flow, Map<String, Set<String>> waitsFor, Set<String> onPath,
                                      Set<String> done, List<String> path) {
        if (onPath.contains(flow)) {
            final List<String> cycle = new ArrayList<>(path.subList(path.indexOf(flow), path.size()));
            cycle.add(flow);
            return cycle;
        }
        if (!done.add(flow)) {
            return List.of();
        }
        onPath.add(flow);
        path.add(flow);
        for (String awaited : waitsFor.getOrDefault(flow, Set.of())) {
            final List<String> cycle = visit(awaited, waitsFor, onPath, done, path);
            if (!cycle.isEmpty()) {
                return cycle;
            }
        }
        onPath.remove(flow);
        path.remove(path.size() - 1);
        return List.of();
    }
}
