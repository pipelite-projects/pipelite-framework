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
package io.pipelite.test;

import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.definition.FlowDefinitionImpl;
import io.pipelite.core.definition.SinkDefinitionImpl;
import io.pipelite.dsl.definition.EndpointDefinition;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.definition.ProcessorDefinition;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.channel.ChannelURL;
import io.pipelite.spi.flow.ExceptionHandler;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.exchange.HeadersImpl;
import io.pipelite.spi.flow.exchange.IdentityGenerator;
import io.pipelite.spi.flow.exchange.Message;
import io.pipelite.spi.flow.exchange.MessageFactory;
import io.pipelite.test.support.impl.CaptureChannelAdapter;
import io.pipelite.test.support.impl.StepSnapshotCapture;
import io.pipelite.test.support.matchers.Action;
import io.pipelite.test.support.matchers.Expectation;
import io.pipelite.test.support.matchers.Precondition;
import io.pipelite.test.support.matchers.StepExpectation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Single entry-point, BDD-style test fixture for Pipelite components.
 *
 * <p>The fluent chain is fully parametric: {@link #given(Precondition...)} takes
 * the test setup as {@link Precondition}s, {@link WhenOperations#when(Action)}
 * takes the component activation as an {@link Action}, and
 * {@link ThenOperations#then(Expectation...)} takes the verifications as
 * {@link Expectation}s. Built-in instances are provided by {@link Preconditions},
 * {@link Actions} and {@link Expectations} respectively.
 *
 * <h3>Processor mode</h3>
 * <p>Executes a single {@link Processor} in complete isolation — no runtime,
 * no endpoints, fully synchronous. Use this to unit-test custom processors or
 * channel-adapter processors without bootstrapping the Pipelite engine.
 *
 * <pre>{@code
 * PipeliteTestFixture.given(
 *         Preconditions.header("Source-System", "Legacy-API"),
 *         Preconditions.inputPayload(Map.of("price", 100)))
 *     .when(Actions.process(myProcessor))
 *     .then(Expectations.isSuccess());
 * }</pre>
 *
 * <h3>Flow mode</h3>
 * <p>Executes one or more complete flow definitions through a managed
 * {@code DefaultPipeliteContext}. The fixture starts the context, supplies the
 * exchange to the entry-point endpoint, waits for the exchange to travel the
 * entire pipeline, and stops the context before evaluating expectations.
 *
 * <p>The flow under test keeps whatever real sink it was written with — e.g.
 * {@code toSink("kafka://orders-out")}. Before the flow is registered with the
 * context, the fixture transparently redirects its terminal sink (any
 * non-{@code link://} endpoint) to an internal {@code test://} capture point
 * handled by {@link CaptureChannelAdapter}, so the real channel adapter is
 * never actually invoked and the test author never needs to know about it.
 * {@code link://} hops between chained flows are left untouched, since only
 * the final flow's real sink should be captured.
 *
 * <pre>{@code
 * PipeliteTestFixture.given(
 *         Preconditions.flowDefinition(flow),
 *         Preconditions.header("X-Order-Id", "ORD-001"),
 *         Preconditions.inputPayload(order))
 *     .when(Actions.supplyTo("orders-in"))
 *     .then(
 *         output(Expectations.isExecutionCompleted()),
 *         step("enrich", Expectations.hasHeader("X-Enriched-By")));
 * }</pre>
 *
 * <p>{@link ThenOperations#getHeaderAs(String, Class)} is kept alongside
 * {@link ThenOperations#getOutputPayloadAs(Class)} even though the spec's
 * interface contract only lists payload accessors — several scenarios need
 * to assert an exact header value, not merely its presence.
 */
public class PipeliteTestFixture implements GivenOperations, ExecutionTarget, WhenOperations, ThenOperations {

    private static final long DEFAULT_TIMEOUT_SECONDS = 5L;
    private static final String LINK_PROTOCOL = "link";

    // --- shared input ---
    private final HeadersImpl headers;
    private Object inputPayload;

    // --- processor mode ---
    private final ExchangeFactory exchangeFactory;
    private Exchange exchange;
    private TestProcessContribution contribution;

    // --- flow mode ---
    private final List<FlowDefinition> flowDefinitions;
    private long timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    private Exchange capturedFlowExchange;
    private boolean flowExecuted;
    private final Map<String, Exchange> stepSnapshots;

    private PipeliteTestFixture() {
        final IdentityGenerator identityGenerator = new DistributedIdentityGeneratorImpl();
        final MessageFactory messageFactory = new DefaultMessageFactory(identityGenerator);
        this.exchangeFactory = new DefaultExchangeFactory(messageFactory);
        this.headers = new HeadersImpl();
        this.flowDefinitions = new ArrayList<>();
        this.stepSnapshots = new ConcurrentHashMap<>();
    }

    /**
     * Applies every {@link Precondition} to a fresh fixture and returns it,
     * ready to be activated via {@link WhenOperations#when(Action)}.
     */
    public static WhenOperations given(Precondition... preconditions) {
        final PipeliteTestFixture fixture = new PipeliteTestFixture();
        for (Precondition precondition : preconditions) {
            precondition.apply(fixture);
        }
        return fixture;
    }

    // -------------------------------------------------------------------------
    // GivenOperations — consumed by Precondition
    // -------------------------------------------------------------------------

    @Override
    public void header(String name, Object value) {
        headers.putHeader(name, value);
    }

    @Override
    public void payload(Object payload) {
        this.inputPayload = payload;
    }

    @Override
    public void flowDefinition(FlowDefinition flowDefinition) {
        flowDefinitions.add(flowDefinition);
    }

    @Override
    public void timeout(long seconds) {
        this.timeoutSeconds = seconds;
    }

    // -------------------------------------------------------------------------
    // WhenOperations — consumed by the public given(...).when(...) chain
    // -------------------------------------------------------------------------

    @Override
    public ThenOperations when(Action action) {
        return action.apply(this);
    }

    // -------------------------------------------------------------------------
    // ExecutionTarget — consumed by Action
    // -------------------------------------------------------------------------

    @Override
    public ThenOperations process(Processor processor) {
        exchange = exchangeFactory.createExchange(headers, inputPayload);
        contribution = new TestProcessContribution();
        try {
            processor.process(exchange, contribution);
        } catch (RuntimeException e) {
            contribution.recordFailure(e);
        }
        return this;
    }

    @Override
    public ThenOperations supplyTo(String entryPointEndpoint) {
        stepSnapshots.clear();
        flowExecuted = true;

        final String testId = UUID.randomUUID().toString();
        final CompletableFuture<Exchange> captureFuture = CaptureChannelAdapter.register(testId);

        final DefaultPipeliteContext context = new DefaultPipeliteContext();
        flowDefinitions.stream().map(this::redirectSinkToCapture).forEach(context::registerFlowDefinition);
        registerStepSnapshotCapture(context.getExchangeFactory());
        context.start();

        Exchange captured = null;
        try {
            final Exchange flowExchange = context.getExchangeFactory().createExchange(headers, inputPayload);
            flowExchange.setProperty(CaptureChannelAdapter.TEST_ID_PROPERTY, testId);
            context.supplyExchange(entryPointEndpoint, flowExchange);
            captured = captureFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException ignored) {
            // flow did not reach the test sink within the timeout
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throw new RuntimeException("Unexpected error waiting for flow capture", e);
        } finally {
            CaptureChannelAdapter.deregister(testId);
            context.stop();
        }

        capturedFlowExchange = captured;
        return this;
    }

    /**
     * Returns a copy of {@code original} with the same flow name, source,
     * processor steps (the very same {@link FlowNode} instances, so
     * {@link #registerStepSnapshotCapture} keeps working) and exception
     * handler, but with its terminal sink redirected to an internal
     * {@code test://} capture endpoint — unless that sink is a {@code link://}
     * hop to another registered flow, which must be left untouched so
     * multi-flow chaining keeps working. A flow with no sink (e.g. a
     * recipient-list sub-flow) is copied with no sink either.
     */
    private FlowDefinition redirectSinkToCapture(FlowDefinition original) {
        final FlowDefinitionImpl copy = new FlowDefinitionImpl(original.getFlowName());
        copy.setSourceDefinition(original.sourceDefinition());
        copy.setExceptionHandler(original.getExceptionHandler(ExceptionHandler.class));

        final Iterator<ProcessorDefinition> processorDefinitions = original.iterateProcessorDefinitions();
        while (processorDefinitions.hasNext()) {
            copy.addProcessorDefinition(processorDefinitions.next());
        }

        final EndpointDefinition endpointDefinition = original.endpointDefinition();
        if (endpointDefinition != null && !LINK_PROTOCOL.equals(ChannelURL.parse(endpointDefinition.getUrl()).getProtocol())) {
            copy.setEndpointDefinition(new SinkDefinitionImpl(CaptureChannelAdapter.CAPTURE_ENDPOINT_URL));
        } else {
            copy.setEndpointDefinition(endpointDefinition);
        }

        return copy;
    }

    private void registerStepSnapshotCapture(ExchangeFactory contextExchangeFactory) {
        final StepSnapshotCapture capture = new StepSnapshotCapture(stepSnapshots, contextExchangeFactory);
        for (FlowDefinition flowDefinition : flowDefinitions) {
            final Iterator<ProcessorDefinition> processorDefinitions = flowDefinition.iterateProcessorDefinitions();
            while (processorDefinitions.hasNext()) {
                final ProcessorDefinition processorDefinition = processorDefinitions.next();
                processorDefinition.getProcessor(FlowNode.class).addExchangePostProcessor(capture);
            }
        }
    }

    // -------------------------------------------------------------------------
    // ThenOperations
    // -------------------------------------------------------------------------

    @Override
    public ThenOperations then(Expectation... expectations) {
        final Exchange exchangeUnderInspection = isFlowMode() ? capturedFlowExchange : exchange;
        for (Expectation expectation : expectations) {
            if (expectation instanceof StepExpectation) {
                final StepExpectation stepExpectation = (StepExpectation) expectation;
                final String stepName = stepExpectation.stepName();
                final Exchange snapshot = stepSnapshots.get(stepName);
                if (snapshot == null) {
                    throw new AssertionError(String.format(
                        "Step '%s' was not reached during flow execution", stepName));
                }
                stepExpectation.verify(snapshot, null);
            } else {
                expectation.verify(exchangeUnderInspection, contribution);
            }
        }
        return this;
    }

    @Override
    public Object getOutputPayload() {
        final Exchange exchangeUnderInspection = requireExchangeAvailable();
        final Message outputMessage = exchangeUnderInspection.getOutput();
        if (exchangeUnderInspection.isOutputSet() && outputMessage.hasPayload()) {
            return outputMessage.getPayload();
        }
        return exchangeUnderInspection.getInputPayload();
    }

    @Override
    public <T> T getOutputPayloadAs(Class<T> expectedType) {
        final Object payload = getOutputPayload();
        if (payload == null) {
            return null;
        }
        if (expectedType.isAssignableFrom(payload.getClass())) {
            return expectedType.cast(payload);
        }
        throw new ClassCastException(String.format("Cannot cast payload from %s to %s",
            payload.getClass().getName(), expectedType.getName()));
    }

    @Override
    public <T> T getHeaderAs(String name, Class<T> expectedType) {
        final Exchange exchangeUnderInspection = requireExchangeAvailable();
        return exchangeUnderInspection.tryGetHeaderAs(name, expectedType).orElse(null);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private boolean isFlowMode() {
        return flowExecuted;
    }

    private Exchange requireExchangeAvailable() {
        if (isFlowMode()) {
            if (capturedFlowExchange == null) {
                throw new IllegalStateException(
                    "Flow execution did not reach its sink — the exchange was filtered or " +
                    "stopped before completion. Ensure your flow has a sink and that no processor " +
                    "calls contribution.stopExecution() unexpectedly.");
            }
            return capturedFlowExchange;
        }
        if (exchange == null) {
            throw new IllegalStateException("No action has been executed yet — call when(...) first.");
        }
        return exchange;
    }
}
