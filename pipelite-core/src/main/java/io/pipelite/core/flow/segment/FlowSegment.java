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
package io.pipelite.core.flow.segment;

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.flow.FlowNodeConfigurer;
import io.pipelite.core.flow.process.DefaultProcessorNode;
import io.pipelite.core.flow.process.WireTapProcessorNode;
import io.pipelite.dsl.segment.FlowSegmentDefinition;
import io.pipelite.dsl.segment.SegmentStep;
import io.pipelite.dsl.segment.SegmentStepDefinition;
import io.pipelite.dsl.segment.SegmentWireTapStep;
import io.pipelite.spi.flow.AbstractFlowNode;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Owns and drives a named, isolated inner chain of {@code FlowNode}s ({@link SegmentStep}/
 * {@link SegmentWireTapStep} entries of a {@link FlowSegmentDefinition}), executed once per
 * incoming Exchange. Does not extend {@code AbstractProcessorNode} - like {@code SplitterNode}/
 * {@code RecipientListRouterNode}, it orchestrates a whole sub-chain of {@code FlowNode}s, not a
 * single {@code Processor} call.
 *
 * <p>Forwards to {@code next} based on the inner chain's own terminal Exchange (see
 * {@link #process(Exchange)}), not by mutating the Exchange it received - the received Exchange's
 * {@code headers}/{@code properties} are immutable references (see {@code Exchange}), so any
 * change made by an inner step would otherwise be silently lost.
 */
public class FlowSegment extends AbstractFlowNode implements PipeliteContextAware {

    private final FlowNode segmentHead;
    private final List<FlowNode> segmentNodes;   // for propagation, not for wiring
    private final FlowSegmentTailNode tail;

    private ExchangeFactory exchangeFactory;

    public FlowSegment(FlowSegmentDefinition definition) {
        Objects.requireNonNull(definition, "definition is required and cannot be null");
        this.tail = new FlowSegmentTailNode();
        this.segmentNodes = new ArrayList<>();

        FlowNode head = tail;
        FlowNode chainTail = null;
        for (SegmentStepDefinition stepDefinition : definition) {
            final FlowNode node = toFlowNode(stepDefinition);
            segmentNodes.add(node);
            if (chainTail == null) {
                head = node;
            } else {
                chainTail.setNext(node);
            }
            chainTail = node;
        }
        if (chainTail != null) {
            chainTail.setNext(tail);
        }
        this.segmentHead = head;
    }

    private static FlowNode toFlowNode(SegmentStepDefinition stepDefinition) {
        if (stepDefinition instanceof SegmentStep) {
            final SegmentStep step = (SegmentStep) stepDefinition;
            final DefaultProcessorNode node = new DefaultProcessorNode(step.getProcessor());
            node.setProcessorName(step.getName());
            return node;
        } else if (stepDefinition instanceof SegmentWireTapStep) {
            final SegmentWireTapStep step = (SegmentWireTapStep) stepDefinition;
            final WireTapProcessorNode node = new WireTapProcessorNode(step.getEndpointURL());
            node.setProcessorName(step.getName());
            return node;
        }
        throw new IllegalStateException("Unsupported segment step definition: " + stepDefinition.getClass());
    }

    @Override
    public void setFlowName(String flowName) {
        super.setFlowName(flowName);
        segmentNodes.forEach(node -> node.setFlowName(flowName));
    }

    @Override
    public void setSourceEndpointResource(String sourceEndpointResource) {
        super.setSourceEndpointResource(sourceEndpointResource);
        segmentNodes.forEach(node -> node.setSourceEndpointResource(sourceEndpointResource));
    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        Objects.requireNonNull(pipeliteContext, "pipeliteContext is required and cannot be null");
        this.exchangeFactory = pipeliteContext.getExchangeFactory();
        // Same injection logic FlowFactory uses for top-level flow nodes (ExchangeFactoryAware/
        // PipeliteContextAware) - shared via FlowNodeConfigurer since these inner nodes are never
        // seen by FlowFactory itself and must be injected here instead.
        segmentNodes.forEach(node -> FlowNodeConfigurer.injectDependencies(node, pipeliteContext));
    }

    @Override
    public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) {
        super.addExchangePreProcessor(exchangePreProcessor);
        segmentNodes.forEach(node -> node.addExchangePreProcessor(exchangePreProcessor));
    }

    @Override
    public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) {
        super.addExchangePostProcessor(exchangePostProcessor);
        segmentNodes.forEach(node -> node.addExchangePostProcessor(exchangePostProcessor));
    }

    @Override
    public void process(Exchange exchange) {

        Exchange collected = null;
        try {
            preProcessExchange(exchange);
            segmentHead.process(exchange);
            // null when a step inside the segment (e.g. a filter) stopped execution before
            // reaching the tail node - same meaning as an ordinary step stopping the main chain,
            // so this segment must not forward to next either (see below).
            collected = tail.consumeResult();
            postProcessExchange(collected != null ? collected : exchange);
        } catch (RuntimeException exception) {
            // Same shape as SplitterNode.process's try/catch: a single dispatch covers the
            // entire segment, never propagated to inner steps (would break the tail node's
            // handoff for a failing last step - see FlowSegmentOperations design rationale).
            if (exceptionHandler != null) {
                exceptionHandler.handleException(exception, exchange);
                return;
            } else {
                throw exception;
            }
        }

        if (collected != null && next != null) {
            final Exchange nextExchange = exchangeFactory.nextExchange(collected);
            next.process(nextExchange);
        }
    }
}
