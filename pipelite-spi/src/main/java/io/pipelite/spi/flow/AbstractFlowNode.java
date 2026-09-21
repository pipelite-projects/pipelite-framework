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
package io.pipelite.spi.flow;

import io.pipelite.common.support.Preconditions;
import io.pipelite.dsl.process.ExceptionHandler;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;
import io.pipelite.spi.flow.process.FlowExecutionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;

public abstract class AbstractFlowNode implements FlowNode {

    private final Collection<ExchangePreProcessor> exchangePreProcessors;
    private final Collection<ExchangePostProcessor> exchangePostProcessors;

    private String flowName;
    private String sourceEndpointResource;
    private String processorName;
    protected String tag;
    protected FlowNode next;
    protected ExceptionHandler exceptionHandler;

    public AbstractFlowNode() {
        exchangePreProcessors = new ArrayList<>();
        exchangePostProcessors = new ArrayList<>();
    }

    @Override
    public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) {
        if(exchangePreProcessor != null){
            exchangePreProcessors.add(exchangePreProcessor);
        }
    }

    public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor){
        if(exchangePostProcessor != null){
            exchangePostProcessors.add(exchangePostProcessor);
        }
    }

    protected void preProcessExchange(ExchangeImpl exchange){

        Preconditions.notNull(flowName, "flowName is required and cannot be null");
        Preconditions.notNull(processorName, "processorName is required and cannot be null");

        final FlowExecutionContext ctx = new FlowExecutionContext(flowName, sourceEndpointResource, processorName);
        exchangePreProcessors
            .stream()
            .sorted(Comparator.comparing(ExchangePreProcessor::getOrder))
            .forEach(exchangePreProcessor -> exchangePreProcessor.preProcess(ctx, exchange));
    }

    protected void postProcessExchange(ExchangeImpl exchange){

        Preconditions.notNull(flowName, "flowName is required and cannot be null");
        Preconditions.notNull(processorName, "processorName is required and cannot be null");

        final FlowExecutionContext ctx = new FlowExecutionContext(flowName, sourceEndpointResource, processorName);
        exchangePostProcessors
            .stream()
            .sorted(Comparator.comparing(ExchangePostProcessor::getOrder))
            .forEach(exchangePostProcessor -> exchangePostProcessor.postProcess(ctx, exchange));
    }

    @Override
    public void setFlowName(String flowName) {
        this.flowName = flowName;
    }

    /**
     * Public (not just accessible to subclasses) so a sibling class in another package — e.g.
     * {@code EventDrivenConsumerService} reading its wrapped {@code EventDrivenConsumer}'s flow
     * name to build a thread name — can call it through an object reference without itself
     * extending this class.
     */
    public String getFlowName() {
        return flowName;
    }

    @Override
    public void setSourceEndpointResource(String sourceEndpointResource) {
        this.sourceEndpointResource = sourceEndpointResource;
    }

    /**
     * Public (not just accessible to subclasses) for the same reason as {@link #getFlowName()} —
     * a sibling class in another package (e.g. whatever wires a per-source {@code DurableInbox}
     * onto a consumer at flow-build time, see issue #70) needs to read this value through an
     * object reference without itself extending this class.
     */
    public String getSourceEndpointResource() {
        return sourceEndpointResource;
    }

    @Override
    public void setProcessorName(String processorName) {
        this.processorName = processorName;
    }

    @Override
    public void tag(String tag) {
        this.tag = tag;
    }

    public void setNext(FlowNode next) {
        this.next = next;
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public FlowNode getNext() {
        return next;
    }

    @Override
    public String getProcessorName() {
        return processorName;
    }

    @Override
    public void setExceptionHandler(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    /**
     * How a node whose work can fail on a message reports the failure (issue #105): it hands it to
     * the flow's exception handler, which is what drives {@code withRetry}, {@code withErrorChannel},
     * {@code withExceptionHandler} and the default handler, marking this node as the failed
     * processor so a retry resumes right here. If the flow has no handler the exception is
     * rethrown, unchanged. The node logs the failure itself, in its own words, before calling this,
     * and returns afterwards: it must not go on to its next node.
     * <p>
     * Used by every node that catches a {@code RuntimeException} around its own work, so the
     * contract lives in one place instead of a copy per node. Not for {@code DefaultConsumer},
     * which never rethrows and marks no failed processor.
     *
     * @throws RuntimeException {@code exception} itself, when there is no exception handler
     */
    protected final void handleFailure(RuntimeException exception, ExchangeImpl exchange) {
        if (exceptionHandler == null) {
            throw exception;
        }
        exchange.setProperty(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, getProcessorName());
        exceptionHandler.handleException(exception, exchange);
    }
}
