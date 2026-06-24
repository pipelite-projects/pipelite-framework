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

import io.pipelite.core.Pipelite;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.test.support.matchers.Actions;
import io.pipelite.test.support.matchers.Expectations;
import io.pipelite.test.support.matchers.Preconditions;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class PipeliteTestFixtureFlowTest {

    // -------------------------------------------------------------------------
    // Real sink redirection — the test author's flow keeps its real sink
    // -------------------------------------------------------------------------

    @Test
    public void givenFlowWithRealUnconfiguredSink_whenSupplyTo_thenRealSinkIsNeverInvokedAndCaptureSucceeds() {
        // "kafka://" has no adapter on pipelite-test-support's classpath (kafka-channel-adapter
        // is only a test-scoped dependency of pipelite-core, so it does not propagate transitively).
        // If the fixture did not redirect this sink before registering the flow, resolving it would
        // throw. The flow definition is exactly what a user would write for production.
        FlowDefinition flow = Pipelite.defineFlow("real-sink-flow")
            .fromSource("in")
            .process("enrich", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(String.class) + "-enriched"))
            .toSink("kafka://orders-out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.inputPayload("order"))
            .when(Actions.supplyTo("in"))
            .then(Expectations.isExecutionCompleted(), Expectations.payloadEquals("order-enriched"));
    }

    // -------------------------------------------------------------------------
    // isCompleted / basic lifecycle
    // -------------------------------------------------------------------------

    @Test
    public void givenFlowWithNoProcessor_whenSupplyTo_thenFlowCompletes() {
        FlowDefinition flow = Pipelite.defineFlow("no-op-flow")
            .fromSource("in")
            .toSink("out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.inputPayload("hello"))
            .when(Actions.supplyTo("in"))
            .then(Expectations.isExecutionCompleted());
    }

    @Test
    public void givenFlowWithNoProcessor_whenSupplyTo_thenInputPayloadPassesThrough() {
        FlowDefinition flow = Pipelite.defineFlow("passthrough-flow")
            .fromSource("in")
            .toSink("out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.inputPayload("original"))
            .when(Actions.supplyTo("in"))
            .then(Expectations.payloadEquals("original"));
    }

    // -------------------------------------------------------------------------
    // Payload transformation
    // -------------------------------------------------------------------------

    @Test
    public void givenProcessorThatTransformsPayload_whenSupplyTo_thenOutputPayloadIsTransformed() {
        FlowDefinition flow = Pipelite.defineFlow("transform-flow")
            .fromSource("in")
            .process("uppercase", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(String.class).toUpperCase()))
            .toSink("out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.inputPayload("hello"))
            .when(Actions.supplyTo("in"))
            .then(Expectations.isExecutionCompleted(), Expectations.payloadEquals("HELLO"));
    }

    @Test
    public void givenMultipleProcessors_whenSupplyTo_thenTransformationsAreChained() {
        FlowDefinition flow = Pipelite.defineFlow("chain-flow")
            .fromSource("in")
            .process("step1", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(Integer.class) * 2))
            .process("step2", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(Integer.class) + 10))
            .toSink("out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.inputPayload(5))
            .when(Actions.supplyTo("in"))
            .then(Expectations.isExecutionCompleted(), Expectations.payloadEquals(20)); // (5*2)+10
    }

    @Test
    public void givenProcessorWithMapPayload_whenSupplyTo_thenGetOutputPayloadAsReturnsTypedMap() {
        Map<String, Object> transformed = new HashMap<>();
        transformed.put("price", 122.0);

        FlowDefinition flow = Pipelite.defineFlow("map-flow")
            .fromSource("in")
            .process("enrich-price", (io, c) -> io.setOutputPayload(transformed))
            .toSink("out")
            .build();

        ThenOperations then = PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.inputPayload(Map.of("price", 100)))
            .when(Actions.supplyTo("in"))
            .then(Expectations.payloadEquals(transformed));

        then.getOutputPayloadAs(Map.class);
    }

    // -------------------------------------------------------------------------
    // Header propagation and mutation
    // -------------------------------------------------------------------------

    @Test
    public void givenInputHeaders_whenSupplyTo_thenHeadersArePropagatedToResult() {
        FlowDefinition flow = Pipelite.defineFlow("header-flow")
            .fromSource("in")
            .process("noop", (io, c) -> io.setOutputPayload(io.getInputPayload()))
            .toSink("out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.header("X-Tenant", "acme"),
                Preconditions.header("X-Correlation-Id", "abc-123"),
                Preconditions.inputPayload("data"))
            .when(Actions.supplyTo("in"))
            .then(
                Expectations.isExecutionCompleted(),
                Expectations.headerEquals("X-Tenant", "acme"),
                Expectations.headerEquals("X-Correlation-Id", "abc-123"));
    }

    @Test
    public void givenProcessorThatAddsHeader_whenSupplyTo_thenNewHeaderIsVisibleInResult() {
        FlowDefinition flow = Pipelite.defineFlow("add-header-flow")
            .fromSource("in")
            .process("tag", (io, c) -> io.putHeader("X-Processed-By", "test-engine"))
            .toSink("out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.inputPayload("data"))
            .when(Actions.supplyTo("in"))
            .then(Expectations.headerEquals("X-Processed-By", "test-engine"));
    }

    @Test
    public void givenIntegerHeader_whenGetHeaderAs_thenReturnsTypedValue() {
        FlowDefinition flow = Pipelite.defineFlow("typed-header-flow")
            .fromSource("in")
            .toSink("out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.header("Retry-Count", 3),
                Preconditions.inputPayload("data"))
            .when(Actions.supplyTo("in"))
            .then(Expectations.headerEquals("Retry-Count", 3));
    }

    @Test
    public void givenAbsentHeader_whenGetHeaderAs_thenReturnsNull() {
        FlowDefinition flow = Pipelite.defineFlow("no-header-flow")
            .fromSource("in")
            .toSink("out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.inputPayload("data"))
            .when(Actions.supplyTo("in"))
            .then(Expectations.noHeader("X-Missing"));
    }

    // -------------------------------------------------------------------------
    // Filtered / stopped execution
    // -------------------------------------------------------------------------

    @Test
    public void givenProcessorCallsStopExecution_whenSupplyTo_thenSinkIsNotReachedAndNotCompleted() {
        FlowDefinition flow = Pipelite.defineFlow("filter-flow")
            .fromSource("in")
            .process("gate", (io, c) -> c.stopExecution())
            .toSink("out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.timeout(1),
                Preconditions.inputPayload("filtered-message"))
            .when(Actions.supplyTo("in"))
            .then(Expectations.isNotExecutionCompleted());
    }

    @Test(expected = IllegalStateException.class)
    public void givenNotCompletedResult_whenGetOutputPayload_thenThrowsIllegalStateException() {
        FlowDefinition flow = Pipelite.defineFlow("filtered-no-inspect-flow")
            .fromSource("in")
            .process("gate", (io, c) -> c.stopExecution())
            .toSink("out")
            .build();

        ThenOperations then = PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.timeout(1),
                Preconditions.inputPayload("x"))
            .when(Actions.supplyTo("in"));

        then.getOutputPayload();
    }

    // -------------------------------------------------------------------------
    // Multi-flow (link://)
    // -------------------------------------------------------------------------

    @Test
    public void givenTwoLinkedFlows_whenSupplyTo_thenBothFlowsExecuteAndCaptureSucceeds() {
        FlowDefinition originFlow = Pipelite.defineFlow("origin-flow")
            .fromSource("entry")
            .process("enrich", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(String.class) + "-enriched"))
            .toSink("link://destination-entry")
            .build();

        FlowDefinition destinationFlow = Pipelite.defineFlow("destination-flow")
            .fromSource("destination-entry")
            .process("finalize", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(String.class) + "-finalized"))
            .toSink("out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(originFlow),
                Preconditions.flowDefinition(destinationFlow),
                Preconditions.inputPayload("msg"))
            .when(Actions.supplyTo("entry"))
            .then(Expectations.isExecutionCompleted(), Expectations.payloadEquals("msg-enriched-finalized"));
    }

    @Test
    public void givenTwoLinkedFlowsWithRealSinkOnDestination_whenSupplyTo_thenLinkHopIsPreservedAndRealSinkIsCaptured() {
        // The link:// hop between the two flows must be left untouched (otherwise the destination
        // flow would never receive the exchange), while only destinationFlow's real, unconfigured
        // "kafka://" sink gets transparently redirected to capture. inspectStep is also exercised on
        // a step from EACH flow, proving step snapshots are captured across the whole chain.
        FlowDefinition originFlow = Pipelite.defineFlow("origin-flow-2")
            .fromSource("entry-2")
            .process("enrich", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(String.class) + "-enriched"))
            .toSink("link://destination-entry-2")
            .build();

        FlowDefinition destinationFlow = Pipelite.defineFlow("destination-flow-2")
            .fromSource("destination-entry-2")
            .process("finalize", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(String.class) + "-finalized"))
            .toSink("kafka://orders-out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(originFlow),
                Preconditions.flowDefinition(destinationFlow),
                Preconditions.header("X-Trace-Id", "trace-42"),
                Preconditions.inputPayload("msg"))
            .when(Actions.supplyTo("entry-2"))
            .then(
                Expectations.isExecutionCompleted(),
                Expectations.payloadEquals("msg-enriched-finalized"),
                Expectations.headerEquals("X-Trace-Id", "trace-42"))
            .inspectStep("enrich", Expectations.payloadEquals("msg-enriched"))
            .inspectStep("finalize", Expectations.payloadEquals("msg-enriched-finalized"));
    }

    // -------------------------------------------------------------------------
    // Payload transformation via transformPayload DSL
    // -------------------------------------------------------------------------

    @Test
    public void givenTransformPayloadDsl_whenSupplyTo_thenPayloadIsTransformed() {
        FlowDefinition flow = Pipelite.defineFlow("dsl-transform-flow")
            .fromSource("in")
            .transformPayload("double-it",
                holder -> holder.getPayloadAs(Integer.class) * 2)
            .toSink("out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.inputPayload(21))
            .when(Actions.supplyTo("in"))
            .then(Expectations.isExecutionCompleted(), Expectations.payloadEquals(42));
    }

    // -------------------------------------------------------------------------
    // Step-by-step inspection
    // -------------------------------------------------------------------------

    @Test
    public void givenMultiStepFlow_whenInspectStep_thenIntermediateStateIsVisible() {
        FlowDefinition flow = Pipelite.defineFlow("inspect-flow")
            .fromSource("in")
            .process("step1", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) * 2))
            .process("step2", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) + 10))
            .toSink("out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.inputPayload(5))
            .when(Actions.supplyTo("in"))
            .then(Expectations.isExecutionCompleted())
            .inspectStep("step1", Expectations.payloadEquals(10))
            .inspectStep("step2", Expectations.payloadEquals(20));
    }

    @Test(expected = AssertionError.class)
    public void givenStepNotReached_whenInspectStep_thenThrowsAssertionError() {
        FlowDefinition flow = Pipelite.defineFlow("unreached-step-flow")
            .fromSource("in")
            .process("gate", (io, c) -> c.stopExecution())
            .process("never-reached", (io, c) -> { /* unreachable */ })
            .toSink("out")
            .build();

        PipeliteTestFixture.given(
                Preconditions.flowDefinition(flow),
                Preconditions.timeout(1),
                Preconditions.inputPayload("x"))
            .when(Actions.supplyTo("in"))
            .inspectStep("never-reached", Expectations.hasHeader("anything"));
    }
}
