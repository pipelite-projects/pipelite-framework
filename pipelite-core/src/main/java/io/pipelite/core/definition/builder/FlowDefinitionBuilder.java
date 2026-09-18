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
import io.pipelite.core.definition.builder.retry.RetryBuilder;
import io.pipelite.core.definition.builder.internal.Builder;
import io.pipelite.core.definition.builder.route.RecipientListBuilder;
import io.pipelite.core.definition.builder.route.RouteDefinitionBuilder;
import io.pipelite.core.definition.builder.split.SplitSegmentBuilder;
import io.pipelite.core.flow.DeadLetterChannelExceptionHandler;
import io.pipelite.core.flow.GlobalDefaultExceptionHandler;
import io.pipelite.core.flow.RetryChannelExceptionHandler;
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.deadletter.DeadLetterQueueExceptionHandler;
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
import io.pipelite.dsl.process.ExceptionHandler;
import io.pipelite.spi.flow.exchange.FlowNode;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class FlowDefinitionBuilder implements FlowOperations {

    private final Builder<FlowDefinitionImpl> builder;

    private final ExpressionParser expressionParser;
    private final ConditionEvaluator conditionEvaluator;
    private final TextExpressionEvaluator textExpressionEvaluator;

    // Accumulated by withRetry(...)/withErrorChannel(...)/withExceptionHandler(...) - the DSL's
    // three mutually-exclusive entry points (issue #91) - composed into a single ExceptionHandler
    // in build() — see resolveExceptionHandler(). retry's own exhaustion action (declared via
    // .onErrorChannel(...)/.onExceptionHandler(...) inside withRetry(...)) reuses the same
    // deadLetterFlowName/builtInDeadLetterQueueRequested/customExceptionHandler fields a bare
    // top-level withErrorChannel(...)/withExceptionHandler(...) would set.
    private String declaredFailureHandling;
    private boolean retryChannelRequested = false;
    private int retryMaxAttempts = RetryBuilder.DEFAULT_MAX_ATTEMPTS;
    private boolean builtInDeadLetterQueueRequested = false;
    private String deadLetterFlowName;
    private ExceptionHandler customExceptionHandler;

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

    /**
     * The retry entry point (issue #91) - unlike before, an exhaustion action is mandatory: the
     * configurator lambda can only return a value by calling
     * {@code retry.onErrorChannel(...)}/{@code retry.onExceptionHandler(...)}, so
     * {@code retry -> retry.maxAttempts(3)} alone no longer type-checks.
     */
    @Override
    public EndOperations withRetry(RetryConfigurator configurator) {
        declareFailureHandling("withRetry(...)");
        final RetryBuilder retryBuilder = new RetryBuilder();
        configurator.configure(retryBuilder);
        retryChannelRequested = true;
        retryMaxAttempts = retryBuilder.getMaxAttempts();
        // retryBuilder.getBackoff() accepted by the DSL but not yet consumed here - separate
        // follow-up issue (see RetryOperations#backoff's own Javadoc).
        final ErrorChannelDefinition errorChannelDefinition = retryBuilder.getErrorChannelDefinition();
        if (errorChannelDefinition != null) {
            applyErrorChannelDefinition(errorChannelDefinition);
        } else {
            customExceptionHandler = retryBuilder.getExceptionHandler();
        }
        return this;
    }

    @Override
    public EndOperations withErrorChannel(ErrorChannelConfigurator configurator) {
        declareFailureHandling("withErrorChannel(...)");
        final ErrorChannelDefinition errorChannelDefinition = configurator.configure(new ErrorChannelBuilder());
        applyErrorChannelDefinition(errorChannelDefinition);
        return this;
    }

    @Override
    public EndOperations withExceptionHandler(ExceptionHandler exceptionHandler) {
        declareFailureHandling("withExceptionHandler(...)");
        this.customExceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler is required and cannot be null");
        return this;
    }

    /**
     * The three entry points are mutually exclusive by type in a fluent chain, but this builder is
     * mutable and returns {@code this}, so holding a {@code BuildOperations} reference and calling
     * a second one would otherwise silently combine them (with {@link #resolveExceptionHandler()}'s
     * fixed priority discarding the loser). Fail fast instead.
     */
    private void declareFailureHandling(String entryPoint) {
        if (declaredFailureHandling != null) {
            throw new IllegalStateException(String.format(
                "%s cannot be combined with %s - withRetry(...), withErrorChannel(...) and " +
                    "withExceptionHandler(...) are mutually exclusive, declare exactly one per flow",
                entryPoint, declaredFailureHandling));
        }
        declaredFailureHandling = entryPoint;
    }

    private void applyErrorChannelDefinition(ErrorChannelDefinition errorChannelDefinition) {
        if (Objects.requireNonNull(errorChannelDefinition.getErrorChannelType()) == ErrorChannelDefinition.ChannelType.DEAD_LETTER_QUEUE) {
            builtInDeadLetterQueueRequested = true;
        } else {
            deadLetterFlowName = errorChannelDefinition.getEndpointURL();
        }
    }

    /**
     * Composes whatever the DSL's three mutually-exclusive entry points (issue #91) resolved into
     * a single {@code ExceptionHandler}. Their mutual exclusivity at the type level means this is
     * never asked to reconcile conflicting declarations — unlike before #91, when a custom
     * handler alongside retry/error-channel could be configured but was silently ignored.
     */
    @Override
    public FlowDefinition build() {
        final ExceptionHandler exceptionHandler = resolveExceptionHandler();
        builder.with(target -> target.setExceptionHandler(exceptionHandler));
        return builder.build();
    }

    /**
     * Falls back to {@link GlobalDefaultExceptionHandler} (issue #87) rather than {@code null}
     * when nothing was declared — see that class's own Javadoc for why a real (if minimal)
     * default handler, instead of no handler at all, is what fixes the inconsistent-behavior-per-
     * layer and stuck-durable-inbox-entry problems that issue tracks.
     */
    private ExceptionHandler resolveExceptionHandler() {
        if (retryChannelRequested) {
            final RetryChannelExceptionHandler handler = new RetryChannelExceptionHandler();
            handler.setMaxAttempts(retryMaxAttempts);
            if (builtInDeadLetterQueueRequested) {
                handler.setExhaustionAction(FlowExecutionDump.ExhaustionAction.BUILT_IN_DLQ);
            } else if (deadLetterFlowName != null) {
                handler.setExhaustionAction(FlowExecutionDump.ExhaustionAction.DEAD_LETTER_FLOW);
                handler.setDeadLetterFlowName(deadLetterFlowName);
            } else {
                handler.setExhaustionAction(FlowExecutionDump.ExhaustionAction.FLOW_EXCEPTION_HANDLER);
                handler.setExhaustionExceptionHandler(customExceptionHandler);
            }
            return handler;
        }
        if (builtInDeadLetterQueueRequested) {
            return new DeadLetterQueueExceptionHandler();
        }
        if (deadLetterFlowName != null) {
            return new DeadLetterChannelExceptionHandler(deadLetterFlowName);
        }
        if (customExceptionHandler != null) {
            return customExceptionHandler;
        }
        return new GlobalDefaultExceptionHandler();
    }

}
