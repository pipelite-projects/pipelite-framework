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

import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;

/**
 * Terminal node appended internally to a segment's inner chain. Captures the Exchange produced
 * by the last segment step, so {@link FlowSegment} can read it back after
 * {@code segmentHead.process(exchange)} returns.
 *
 * <p>Uses a {@link ThreadLocal} rather than a plain field: {@code FlowNode} instances are built
 * once and reused for every message that flows through the parent {@code FlowSegment}, including
 * concurrent, unrelated invocations from different threads on the same shared instance.
 */
final class FlowSegmentTailNode implements FlowNode {

    private final ThreadLocal<Exchange> result = new ThreadLocal<>();

    @Override
    public void process(Exchange exchange) {
        exchange.forwardIfNecessary();
        result.set(exchange);
    }

    Exchange consumeResult() {
        final Exchange exchange = result.get();
        result.remove();
        return exchange;
    }

    @Override
    public void setNext(FlowNode next) {
        // terminal node: no-op
    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public void setFlowName(String flowName) {
        // not applicable: never a named flow step
    }

    @Override
    public void setSourceEndpointResource(String sourceEndpointResource) {
        // not applicable: never a named flow step
    }

    @Override
    public void setProcessorName(String processorName) {
        // not applicable: never a named flow step
    }

    @Override
    public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) {
        // not applicable: internal implementation detail of FlowSegment, never observed externally
    }

    @Override
    public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) {
        // not applicable: internal implementation detail of FlowSegment, never observed externally
    }
}
