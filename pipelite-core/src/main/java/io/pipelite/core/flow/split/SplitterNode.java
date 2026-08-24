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
package io.pipelite.core.flow.split;

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.flow.FlowNodeConfigurer;
import io.pipelite.core.flow.process.DefaultProcessorNode;
import io.pipelite.dsl.Headers;
import io.pipelite.dsl.split.SplitSegment;
import io.pipelite.dsl.split.SplitStep;
import io.pipelite.spi.flow.AbstractFlowNode;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.HeadersImpl;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Splits a {@code Collection} payload into independent child Exchanges, drives each one
 * synchronously through an inner segment of steps on the calling thread, then aggregates
 * the results back into the original Exchange before continuing the flow.
 *
 * <p>Happy-path scope only: sequential, single-threaded per invocation, no per-item failure
 * isolation. Does not extend {@code AbstractProcessorNode} — it orchestrates an entire
 * sub-chain of {@code FlowNode}s once per collection item, not a single {@code Processor}
 * call, the same reason {@code RecipientListRouterNode} extends {@code AbstractFlowNode}
 * directly instead.
 */
public class SplitterNode extends AbstractFlowNode implements PipeliteContextAware {

    private final FlowNode segmentHead;
    private final List<FlowNode> segmentNodes;   // for propagation, not for wiring
    private final SplitResultCollectorNode collector;

    private ExchangeFactory exchangeFactory;
    private AggregateRepository aggregateRepository;
    private Aggregator aggregator;

    public SplitterNode(SplitSegment segment) {
        Objects.requireNonNull(segment, "segment is required and cannot be null");
        this.collector = new SplitResultCollectorNode();
        this.segmentNodes = new ArrayList<>();

        FlowNode head = collector;
        FlowNode tail = null;
        for (SplitStep step : segment) {
            final DefaultProcessorNode node = new DefaultProcessorNode(step.getProcessor());
            node.setProcessorName(step.getName());
            segmentNodes.add(node);
            if (tail == null) {
                head = node;
            } else {
                tail.setNext(node);
            }
            tail = node;
        }
        if (tail != null) {
            tail.setNext(collector);
        }
        this.segmentHead = head;
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
        this.aggregateRepository = pipeliteContext.getAggregateRepository();
        this.aggregator = new UseOriginalAggregator(aggregateRepository);
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

        final String splitId = exchange.getInput().getId();
        Exchange aggregated;

        try {
            preProcessExchange(exchange);
            aggregateRepository.save(splitId, exchange);

            final Collection<?> items = exchange.getInputPayloadAs(Collection.class);
            final List<Object> results = new ArrayList<>();

            for (Object item : items) {
                final Headers childHeaders = new HeadersImpl((HeadersImpl) exchange.getHeaders());
                final Exchange child = exchangeFactory.createExchange(childHeaders, item);
                segmentHead.process(child);
                final Exchange collected = collector.consumeResult();
                results.add(collected.getOutput().getPayloadAs(Object.class));
            }

            aggregated = aggregator.aggregate(splitId, results);
            postProcessExchange(aggregated);

        } catch (RuntimeException exception) {
            // Same shape as AbstractProcessorNode.process's try/catch, written explicitly
            // (not inherited) because the work being wrapped here is the whole split loop,
            // not a single Processor call. No exceptionHandler is propagated to inner segment
            // nodes (would break the collector for a failing last step) - a single dispatch
            // covers the entire split, never a per-item one.
            if (exceptionHandler != null) {
                // D13: intentionally the whole pre-split collection, not the single failing
                // item — a resume/dead-letter targeting this property lands back on the split
                // step itself, atomically re-running/dead-lettering the entire batch. See
                // 2026-Q3-pipelite-dead-letter-channel-review.md §3.7 for the reasoning.
                exchange.setProperty(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, getProcessorName());
                exceptionHandler.handleException(exception, exchange);
                return;
            } else {
                throw exception;
            }
        } finally {
            // Never leave an orphaned entry behind, on the happy path or the exception path.
            aggregateRepository.remove(splitId);
        }

        if (next != null) {
            final Exchange nextExchange = exchangeFactory.nextExchange(aggregated);
            next.process(nextExchange);
        }
    }
}
