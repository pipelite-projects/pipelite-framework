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

import io.pipelite.core.definition.internal.*;
import io.pipelite.core.definition.builder.error.ErrorChannelBuilder;
import io.pipelite.core.definition.builder.error.RetryChannelBuilder;
import io.pipelite.core.definition.builder.internal.Builder;
import io.pipelite.core.definition.builder.route.RecipientListBuilder;
import io.pipelite.core.definition.builder.route.RouteDefinitionBuilder;
import io.pipelite.core.definition.builder.split.SplitSegmentBuilder;
import io.pipelite.core.flow.DeadLetterChannelExceptionHandler;
import io.pipelite.core.flow.GlobalDefaultExceptionHandler;
import io.pipelite.core.flow.RetryChannelExceptionHandler;
import io.pipelite.core.flow.expression.TextExpressionEvaluator;
import io.pipelite.core.flow.process.ProcessorNodeFactory;
import io.pipelite.core.flow.process.filter.ExpressionFilterNodeFactory;
import io.pipelite.core.flow.process.transform.PayloadTransformerNodeFactory;
import io.pipelite.core.flow.route.ExpressionConditionEvaluator;
import io.pipelite.core.flow.route.RouteNodeFactory;
import io.pipelite.core.flow.split.SplitNodeFactory;
import io.pipelite.dsl.definition.*;
import io.pipelite.dsl.definition.builder.*;
import io.pipelite.dsl.process.PayloadTransformer;
import io.pipelite.dsl.process.Processor;
import io.pipelite.dsl.route.ConditionEvaluator;
import io.pipelite.dsl.route.RecipientList;
import io.pipelite.dsl.route.RoutingTable;
import io.pipelite.dsl.split.SplitConfigurator;
import io.pipelite.dsl.split.SplitSegment;
import io.pipelite.expression.ExpressionParser;
import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.exchange.FlowNode;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class FlowDefinitionBuilder implements FlowOperations {

    private final Builder<FlowDefinitionImpl> builder;

    private final ExpressionParser expressionParser;
    private final ConditionEvaluator conditionEvaluator;
    private final TextExpressionEvaluator textExpressionEvaluator;

    // Accumulated by withRetryChannel(...)/withErrorChannel(...), composed into a single
    // ExceptionHandler in build() — see resolveExceptionHandler().
    private boolean retryChannelRequested = false;
    private int retryMaxAttempts = RetryChannelBuilder.DEFAULT_MAX_ATTEMPTS;
    private String deadLetterFlowName;

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
    public <C extends SourceConfigurer> SourceOperations fromSource(String url, Consumer<C> configurer) {
        Objects.requireNonNull(configurer, "configurer is required and cannot be null");
        // Erased here, deliberately: the concrete configurer instance this callback actually
        // expects isn't known until DefaultEndpointFactory resolves the real adapter for `url`,
        // at PipeliteContext#start() - see SourceConfigurer's own Javadoc. Safe in practice: a
        // mismatched configurer (e.g. a (FileSourceConfigurer) lambda supplied for a "kafka://"
        // url) surfaces as a ClassCastException the moment the callback is finally invoked against
        // the wrong concrete type, which DefaultEndpointFactory catches and rewraps clearly.
        @SuppressWarnings("unchecked")
        final Consumer<SourceConfigurer> erasedConfigurer = (Consumer<SourceConfigurer>) configurer;
        final SourceDefinition sourceDefinition = new SourceDefinitionImpl(url, erasedConfigurer);
        builder.with(target -> target.setSourceDefinition(sourceDefinition));
        return this;
    }

    @Override
    public ProcessOperations process(String name, Processor processor) {
        final FlowNode processorNode = ProcessorNodeFactory.wrap(processor);
        final ProcessorDefinition processorDefinition = new ProcessorDefinitionImpl(name, processorNode);
        builder.with(target -> target.addProcessorDefinition(processorDefinition));
        return this;
    }

    @Override
    public ProcessOperations wireTap(String name, String endpointURL) {
        final FlowNode processorNode = ProcessorNodeFactory.wireTap(endpointURL);
        final ProcessorDefinition processorDefinition = new ProcessorDefinitionImpl(name, processorNode);
        builder.with(target -> target.addProcessorDefinition(processorDefinition));
        return this;
    }

    @Override
    public ProcessOperations transformPayload(String name, PayloadTransformer payloadTransformer) {
        final Processor processor = PayloadTransformerNodeFactory.create(payloadTransformer);
        final FlowNode processorNode = ProcessorNodeFactory.wrap(processor);
        final ProcessorDefinition processorDefinition = new ProcessorDefinitionImpl(name, processorNode);
        builder.with(target -> target.addProcessorDefinition(processorDefinition));
        return this;
    }

    @Override
    public ProcessOperations filter(String name, String expression) {
        final Processor processor = ExpressionFilterNodeFactory.create(expression, expressionParser);
        final FlowNode processorNode = ProcessorNodeFactory.wrap(processor);
        final ProcessorDefinition processorDefinition = new ProcessorDefinitionImpl(name, processorNode);
        builder.with(target -> target.addProcessorDefinition(processorDefinition));
        return this;
    }

    @Override
    public BuildOperations toRoute(Function<RouteConfigurator, RoutingTable<?>> configurator) {
        final RoutingTable<?> routingTable = configurator.apply(new RouteDefinitionBuilder(conditionEvaluator));
        final FlowNode routerNode = RouteNodeFactory.router(routingTable, textExpressionEvaluator);
        final ProcessorDefinition routerDefinition = new ProcessorDefinitionImpl("to-route", routerNode);
        builder.with(target -> target.addProcessorDefinition(routerDefinition));
        return this;
    }

    @Override
    @Deprecated
    public BuildOperations toRecipientList(RecipientListConfigurator configurator) {
        final RecipientList recipientList = configurator.configure(new RecipientListBuilder());
        final FlowNode recipientListNode = RouteNodeFactory.recipientList(recipientList, conditionEvaluator);
        final ProcessorDefinition recipientListNodeDefinition = new ProcessorDefinitionImpl("to-recipient-list", recipientListNode);
        builder.with(target -> target.addProcessorDefinition(recipientListNodeDefinition));
        return this;
    }

    @Override
    public ProcessOperations split(String name, SplitConfigurator configurator) {
        final SplitSegment segment = configurator.configure(new SplitSegmentBuilder());
        final FlowNode splitNode = SplitNodeFactory.splitter(segment);
        final ProcessorDefinition splitDefinition = new ProcessorDefinitionImpl(name, splitNode);
        builder.with(target -> target.addProcessorDefinition(splitDefinition));
        return this;
    }

    @Override
    public SinkOperations toSink(String url) {
        final SinkDefinition sinkDefinition = new SinkDefinitionImpl(url);
        builder.with(target -> target.setEndpointDefinition(sinkDefinition));
        return this;
    }

    @Override
    public BuildOperations withRetryChannel() {
        retryChannelRequested = true;
        return this;
    }

    @Override
    public BuildOperations withRetryChannel(RetryChannelConfigurator configurator) {
        final RetryChannelBuilder retryChannelBuilder = new RetryChannelBuilder();
        configurator.configure(retryChannelBuilder);
        retryChannelRequested = true;
        retryMaxAttempts = retryChannelBuilder.getMaxAttempts();
        return this;
    }

    @Override
    public BuildOperations withErrorChannel(ErrorChannelConfigurator configurator) {

        final ErrorChannelDefinition errorChannelDefinition = configurator.configure(new ErrorChannelBuilder());
        final ErrorChannelDefinition.ChannelType channelType = errorChannelDefinition.getErrorChannelType();

        if (Objects.requireNonNull(channelType) == ErrorChannelDefinition.ChannelType.RETRY_CHANNEL) {
            retryChannelRequested = true;
        } else {
            deadLetterFlowName = errorChannelDefinition.getEndpointURL();
        }
        return this;
    }

    /**
     * Composes whatever combination of {@code withRetryChannel(...)}/{@code withErrorChannel(...)}
     * was declared into a single {@code ExceptionHandler} — retry alone, dead-letter alone
     * (routes on the first failure, no retry), or both together (retry first, dead-letter only
     * once {@code RetryStrategyFilter} exhausts attempts; see {@code RetryChannelExceptionHandler}).
     * Previously these two DSL methods each wrote directly to the same single
     * {@code exceptionHandler} field, so declaring both on one flow meant the second call
     * silently discarded the first.
     */
    @Override
    public FlowDefinition build() {
        final ExceptionHandler exceptionHandler = resolveExceptionHandler();
        builder.with(target -> target.setExceptionHandler(exceptionHandler));
        return builder.build();
    }

    /**
     * Falls back to {@link GlobalDefaultExceptionHandler} (issue #87) rather than {@code null}
     * when neither {@code withRetryChannel(...)} nor {@code withErrorChannel(...)} was declared —
     * see that class's own Javadoc for why a real (if minimal) default handler, instead of no
     * handler at all, is what fixes the inconsistent-behavior-per-layer and stuck-durable-inbox-
     * entry problems this issue tracks.
     */
    private ExceptionHandler resolveExceptionHandler() {
        if (retryChannelRequested) {
            final RetryChannelExceptionHandler handler = new RetryChannelExceptionHandler();
            handler.setMaxAttempts(retryMaxAttempts);
            handler.setDeadLetterFlowName(deadLetterFlowName);
            return handler;
        }
        if (deadLetterFlowName != null) {
            return new DeadLetterChannelExceptionHandler(deadLetterFlowName);
        }
        return new GlobalDefaultExceptionHandler();
    }

}
