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
package io.pipelite.spi.flow.exchange;

import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;

public interface FlowNode {

    void process(Exchange exchange);

    void setNext(FlowNode next);
    boolean hasNext();

    void setFlowName(String flowName);
    void setSourceEndpointResource(String sourceEndpointResource);
    void setProcessorName(String processorName);

    /**
     * The next node in this flow's own processor chain, or {@code null} if this node is
     * terminal ({@link #hasNext()} is {@code false}) or doesn't participate in name-based
     * lookup (e.g. a router node, which dispatches elsewhere via {@code supplyExchange}
     * rather than continuing this chain). Default {@code null} so existing {@code FlowNode}
     * implementations that predate this method don't have to change.
     */
    default FlowNode getNext(){ return null; }

    /**
     * This node's own processor name, as set by {@link #setProcessorName(String)} — or
     * {@code null} if never set / not applicable to this node. Default {@code null} for the
     * same backward-compatibility reason as {@link #getNext()}.
     */
    default String getProcessorName(){ return null; }

    void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor);
    void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor);

    default void tag(String tag){}
    default void setExceptionHandler(ExceptionHandler exceptionHandler){}

}
