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
package io.pipelite.core.definition.builder;

import io.pipelite.core.definition.*;
import io.pipelite.core.definition.builder.error.ErrorChannelBuilder;
import io.pipelite.core.definition.builder.route.RecipientListBuilder;
import io.pipelite.core.definition.builder.route.RouteDefinitionBuilder;
import io.pipelite.core.flow.GlobalDefaultExceptionHandler;
import io.pipelite.core.flow.RetryChannelExceptionHandler;
import io.pipelite.core.flow.expression.TextExpressionEvaluator;
import io.pipelite.core.flow.process.DefaultProcessorNode;
import io.pipelite.core.flow.process.WireTapProcessorNode;
import io.pipelite.core.flow.process.filter.ExpressionFilterNode;
import io.pipelite.core.flow.process.transform.PayloadTransformerNode;
import io.pipelite.core.flow.route.ExpressionConditionEvaluator;
import io.pipelite.core.flow.route.RecipientListRouterNode;
import io.pipelite.core.flow.route.RouterNode;
import io.pipelite.dsl.definition.*;
import io.pipelite.dsl.definition.builder.*;
import io.pipelite.dsl.process.PayloadTransformer;
import io.pipelite.dsl.process.Processor;
import io.pipelite.dsl.route.ConditionEvaluator;
import io.pipelite.dsl.route.RecipientList;
import io.pipelite.dsl.route.RoutingTable;
import io.pipelite.expression.ExpressionParser;
import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.exchange.FlowNode;

import java.util.Objects;
import java.util.function.Function;

public class FlowDefinitionBuilder implements FlowOperations {

    private final Builder<FlowDefinitionImpl> builder;

    private final ExpressionParser expressionParser;
    private final ConditionEvaluator conditionEvaluator;
    private final TextExpressionEvaluator textExpressionEvaluator;

    public FlowDefinitionBuilder(String flowName){

        assert flowName != null : "parameter flowName is required and cannot be null.";

        builder = Builder.forType(FlowDefinitionImpl.class);
        builder.constructWith(flowName);

        expressionParser = new ExpressionParser();
        conditionEvaluator = new ExpressionConditionEvaluator(expressionParser);
        textExpressionEvaluator = new TextExpressionEvaluator(expressionParser);

    }

    @Override
    public SourceOperations fromSource(String url) {
        final SourceDefinition sourceDefinition = new SourceDefinitionImpl(url);
        builder.with(target -> target.setSourceDefinition(sourceDefinition));
        return this;
    }

    @Override
    public ProcessOperations process(String name, Processor processor) {
        final FlowNode processorNode = new DefaultProcessorNode(processor);
        final ProcessorDefinition processorDefinition = new ProcessorDefinitionImpl(name, processorNode);
        builder.with(target -> target.addProcessorDefinition(processorDefinition));
        return this;
    }

    @Override
    public ProcessOperations wireTap(String name, String endpointURL) {
        final FlowNode processorNode = new WireTapProcessorNode(endpointURL);
        final ProcessorDefinition processorDefinition = new ProcessorDefinitionImpl(name, processorNode);
        builder.with(target -> target.addProcessorDefinition(processorDefinition));
        return this;
    }

    @Override
    public ProcessOperations transformPayload(String name, PayloadTransformer payloadTransformer) {
        final Processor processor = new PayloadTransformerNode(payloadTransformer);
        final FlowNode processorNode = new DefaultProcessorNode(processor);
        final ProcessorDefinition processorDefinition = new ProcessorDefinitionImpl(name, processorNode);
        builder.with(target -> target.addProcessorDefinition(processorDefinition));
        return this;
    }

    @Override
    public ProcessOperations filter(String name, String expression) {
        final Processor processor = new ExpressionFilterNode(expression, expressionParser);
        final FlowNode processorNode = new DefaultProcessorNode(processor);
        final ProcessorDefinition processorDefinition = new ProcessorDefinitionImpl(name, processorNode);
        builder.with(target -> target.addProcessorDefinition(processorDefinition));
        return this;
    }

    @Override
    public BuildOperations toRoute(Function<RouteConfigurator, RoutingTable<?>> configurator) {
        final RoutingTable<?> routingTable = configurator.apply(new RouteDefinitionBuilder(conditionEvaluator));
        final FlowNode routerNode = new RouterNode(routingTable, textExpressionEvaluator);
        final ProcessorDefinition routerDefinition = new ProcessorDefinitionImpl("to-route", routerNode);
        builder.with(target -> target.addProcessorDefinition(routerDefinition));
        return this;
    }

    @Override
    @Deprecated
    public BuildOperations toRecipientList(RecipientListConfigurator configurator) {
        final RecipientList recipientList = configurator.configure(new RecipientListBuilder());
        final FlowNode recipientListNode = new RecipientListRouterNode(recipientList, conditionEvaluator);
        final ProcessorDefinition recipientListNodeDefinition = new ProcessorDefinitionImpl("to-recipient-list", recipientListNode);
        builder.with(target -> target.addProcessorDefinition(recipientListNodeDefinition));
        return this;
    }

    @Override
    public SinkOperations toSink(String url) {
        final SinkDefinition sinkDefinition = new SinkDefinitionImpl(url);
        builder.with(target -> target.setEndpointDefinition(sinkDefinition));
        return this;
    }

    @Override
    public EndOperations withRetryChannel() {
        builder.with(target -> target.setExceptionHandler(new RetryChannelExceptionHandler()));
        return this;
    }

    @Override
    public EndOperations withErrorChannel(ErrorChannelConfigurator configurator) {

        final ErrorChannelDefinition errorChannelDefinition = configurator.configure(new ErrorChannelBuilder());
        final ErrorChannelDefinition.ChannelType channelType = errorChannelDefinition.getErrorChannelType();
        final ExceptionHandler exceptionHandler;

        if (Objects.requireNonNull(channelType) == ErrorChannelDefinition.ChannelType.RETRY_CHANNEL) {
            exceptionHandler = new RetryChannelExceptionHandler();
        } else {
            exceptionHandler = new GlobalDefaultExceptionHandler();
        }
        builder.with(target -> target.setExceptionHandler(exceptionHandler));
        return this;
    }

    @Override
    public FlowDefinition build() {
        return builder.build();
    }

}
