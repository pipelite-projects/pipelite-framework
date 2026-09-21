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

import io.pipelite.core.context.internal.DeclaredDestination;
import io.pipelite.core.context.internal.DeclaresDestinations;
import io.pipelite.core.support.expression.ExpressionUtils;
import io.pipelite.dsl.ChannelProtocols;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.definition.ProcessorDefinition;
import io.pipelite.dsl.definition.SinkDefinition;
import io.pipelite.dsl.process.ExceptionHandler;
import io.pipelite.spi.channel.ChannelURL;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.FlowNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reference integrity (issue #88): every {@code queue://x} a flow sends to must be the queue that
 * a flow in the same context reads, {@code fromSource("queue://x")}. Anything else is a queue
 * nothing reads or, before #100, was simply lost.
 * <p>
 * What is checked: the {@code queue://} destinations of {@code toSink(...)}, of the routes, of the
 * recipient list, of {@code wireTap(...)}, and of {@code toChannel(...)} whether alone or as the
 * exhaustion action of a retry. The rule is the one {@code QueueChannelAdapter} applies at runtime:
 * a target resolves only to a flow whose source is a queue.
 * <p>
 * What is not: a destination with any other protocol (an external system), one that contains an
 * expression (only known when an exchange is routed), and a bare name, which is not a destination
 * at all and is rejected when the flow is defined (issue #102).
 */
public final class FlowReferenceValidator implements ContextValidator {

    @Override
    public void validate(ValidationContext context, ValidationReport report) {

        final Set<String> queues = readQueueNames(context);

        for (FlowDefinition flow : context.flowDefinitions()) {
            for (DeclaredDestination destination : destinationsOf(flow, context)) {
                checkDestination(flow.getFlowName(), destination, queues, report);
            }
        }
    }

    private static void checkDestination(String flowName, DeclaredDestination destination,
                                         Set<String> queues, ValidationReport report) {

        final String url = destination.url();
        if (url == null || ExpressionUtils.hasExpressionText(url)) {
            return;
        }

        final Optional<String> targetQueue = queueName(url);
        if (targetQueue.isPresent() && !queues.contains(targetQueue.get())) {
            report.error(flowName, String.format(
                "%s: target '%s' has no registered flow declaring fromSource(\"%s\")",
                destination.declaredBy(), url, ChannelProtocols.queueURL(targetQueue.get())));
        }
    }

    /**
     * The name of the queue a {@code queue://} URL addresses, empty for anything else.
     */
    private static Optional<String> queueName(String url) {
        try {
            final ChannelURL channelURL = ChannelURL.parse(url);
            if (channelURL.hasProtocol() && ChannelProtocols.QUEUE.equals(channelURL.getProtocol())) {
                return Optional.of(EndpointURL.parse(channelURL.getEndpointURL()).getResource());
            }
        } catch (RuntimeException malformed) {
            // Not this validator's finding: the endpoint that uses it fails on its own, clearly.
        }
        return Optional.empty();
    }

    /**
     * The queues a {@code queue://} destination can reach: the ones the flows read, resolved the
     * way the endpoint factory resolves them.
     */
    private static Set<String> readQueueNames(ValidationContext context) {
        final Set<String> names = new HashSet<>();
        for (FlowDefinition flow : context.flowDefinitions()) {
            QueueSources.nameOf(flow, context).ifPresent(names::add);
        }
        return names;
    }

    private static List<DeclaredDestination> destinationsOf(FlowDefinition flow, ValidationContext context) {

        final List<DeclaredDestination> destinations = new ArrayList<>();

        final SinkDefinition sink = flow.sinkDefinition();
        if (sink != null) {
            try {
                destinations.add(new DeclaredDestination(context.resolveURL(sink.getUrl()), "toSink(...)"));
            } catch (RuntimeException unresolvable) {
                // Not this validator's finding, see readQueueNames.
            }
        }

        final Iterator<ProcessorDefinition> processors = flow.iterateProcessorDefinitions();
        while (processors.hasNext()) {
            final FlowNode node = processors.next().getProcessor(FlowNode.class);
            if (node instanceof DeclaresDestinations declaring) {
                destinations.addAll(declaring.declaredDestinations());
            }
        }

        final ExceptionHandler handler = flow.getExceptionHandler(ExceptionHandler.class);
        if (handler instanceof DeclaresDestinations declaring) {
            destinations.addAll(declaring.declaredDestinations());
        }

        return destinations;
    }

}
