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
import io.pipelite.core.context.DuplicateFlowDefinitionException;
import io.pipelite.dsl.definition.FlowDefinition;
import static io.pipelite.test.PipeliteTest.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

        given(
                flowDefinition(flow),
                inputPayload("order"))
            .when(supplyTo("in"))
            .then(isExecutionCompleted(), payloadEquals("order-enriched"));
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

        given(
                flowDefinition(flow),
                inputPayload("hello"))
            .when(supplyTo("in"))
            .then(isExecutionCompleted());
    }

    @Test
    public void givenFlowWithNoProcessor_whenSupplyTo_thenInputPayloadPassesThrough() {
        FlowDefinition flow = Pipelite.defineFlow("passthrough-flow")
            .fromSource("in")
            .toSink("out")
            .build();

        given(
                flowDefinition(flow),
                inputPayload("original"))
            .when(supplyTo("in"))
            .then(payloadEquals("original"));
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

        given(
                flowDefinition(flow),
                inputPayload("hello"))
            .when(supplyTo("in"))
            .then(isExecutionCompleted(), payloadEquals("HELLO"));
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

        given(
                flowDefinition(flow),
                inputPayload(5))
            .when(supplyTo("in"))
            .then(isExecutionCompleted(), payloadEquals(20)); // (5*2)+10
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

        ThenOperations then = given(
                flowDefinition(flow),
                inputPayload(Map.of("price", 100)))
            .when(supplyTo("in"))
            .then(payloadEquals(transformed));

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

        given(
                flowDefinition(flow),
                header("X-Tenant", "acme"),
                header("X-Correlation-Id", "abc-123"),
                inputPayload("data"))
            .when(supplyTo("in"))
            .then(
                isExecutionCompleted(),
                headerEquals("X-Tenant", "acme"),
                headerEquals("X-Correlation-Id", "abc-123"));
    }

    @Test
    public void givenProcessorThatAddsHeader_whenSupplyTo_thenNewHeaderIsVisibleInResult() {
        FlowDefinition flow = Pipelite.defineFlow("add-header-flow")
            .fromSource("in")
            .process("tag", (io, c) -> io.putHeader("X-Processed-By", "test-engine"))
            .toSink("out")
            .build();

        given(
                flowDefinition(flow),
                inputPayload("data"))
            .when(supplyTo("in"))
            .then(headerEquals("X-Processed-By", "test-engine"));
    }

    @Test
    public void givenIntegerHeader_whenGetHeaderAs_thenReturnsTypedValue() {
        FlowDefinition flow = Pipelite.defineFlow("typed-header-flow")
            .fromSource("in")
            .toSink("out")
            .build();

        given(
                flowDefinition(flow),
                header("Retry-Count", 3),
                inputPayload("data"))
            .when(supplyTo("in"))
            .then(headerEquals("Retry-Count", 3));
    }

    @Test
    public void givenAbsentHeader_whenGetHeaderAs_thenReturnsNull() {
        FlowDefinition flow = Pipelite.defineFlow("no-header-flow")
            .fromSource("in")
            .toSink("out")
            .build();

        given(
                flowDefinition(flow),
                inputPayload("data"))
            .when(supplyTo("in"))
            .then(noHeader("X-Missing"));
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

        given(
                flowDefinition(flow),
                timeout(1),
                inputPayload("filtered-message"))
            .when(supplyTo("in"))
            .then(isNotExecutionCompleted());
    }

    @Test(expected = IllegalStateException.class)
    public void givenNotCompletedResult_whenGetOutputPayload_thenThrowsIllegalStateException() {
        FlowDefinition flow = Pipelite.defineFlow("filtered-no-inspect-flow")
            .fromSource("in")
            .process("gate", (io, c) -> c.stopExecution())
            .toSink("out")
            .build();

        ThenOperations then = given(
                flowDefinition(flow),
                timeout(1),
                inputPayload("x"))
            .when(supplyTo("in"));

        then.getOutputPayload();
    }

    @Test(expected = AssertionError.class)
    public void givenFilteredFlow_whenIsExecutionCompletedAsserted_thenAssertionError() {
        FlowDefinition flow = Pipelite.defineFlow("filter-flow-wrong-assertion")
            .fromSource("in-wrong-1")
            .process("gate", (io, c) -> c.stopExecution())
            .toSink("out")
            .build();

        given(
                flowDefinition(flow),
                timeout(1),
                inputPayload("filtered-message"))
            .when(supplyTo("in-wrong-1"))
            .then(isExecutionCompleted());
    }

    @Test(expected = AssertionError.class)
    public void givenCompletedFlow_whenIsNotExecutionCompletedAsserted_thenAssertionError() {
        FlowDefinition flow = Pipelite.defineFlow("completed-flow-wrong-assertion")
            .fromSource("in-wrong-2")
            .toSink("out")
            .build();

        given(
                flowDefinition(flow),
                inputPayload("hello"))
            .when(supplyTo("in-wrong-2"))
            .then(isNotExecutionCompleted());
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

        given(
                flowDefinition(originFlow),
                flowDefinition(destinationFlow),
                inputPayload("msg"))
            .when(supplyTo("entry"))
            .then(isExecutionCompleted(), payloadEquals("msg-enriched-finalized"));
    }

    @Test
    public void givenTwoLinkedFlowsWithRealSinkOnDestination_whenSupplyTo_thenLinkHopIsPreservedAndRealSinkIsCaptured() {
        // The link:// hop between the two flows must be left untouched (otherwise the destination
        // flow would never receive the exchange), while only destinationFlow's real, unconfigured
        // "kafka://" sink gets transparently redirected to capture. step(...) assertions are also
        // verified on a step from EACH flow, proving step snapshots are captured across the whole chain.
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

        given(
                flowDefinition(originFlow),
                flowDefinition(destinationFlow),
                header("X-Trace-Id", "trace-42"),
                inputPayload("msg"))
            .when(supplyTo("entry-2"))
            .then(
                output(isExecutionCompleted(), payloadEquals("msg-enriched-finalized"), headerEquals("X-Trace-Id", "trace-42")),
                step("enrich", payloadEquals("msg-enriched")),
                step("finalize", payloadEquals("msg-enriched-finalized")));
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

        given(
                flowDefinition(flow),
                inputPayload(21))
            .when(supplyTo("in"))
            .then(isExecutionCompleted(), payloadEquals(42));
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

        given(
                flowDefinition(flow),
                inputPayload(5))
            .when(supplyTo("in"))
            .then(
                output(isExecutionCompleted()),
                step("step1", payloadEquals(10)),
                step("step2", payloadEquals(20)));
    }

    @Test
    public void givenReusedFlowDefinition_whenSupplyToTwice_thenStepSnapshotReflectsCurrentRun() {
        // Regression test for Finding #9: reusing the same FlowDefinition object across
        // multiple supplyTo() calls accumulates StepSnapshotCapture instances on the shared
        // FlowNode. The deactivate() mechanism ensures earlier captures become no-ops so
        // only the current run's snapshot is visible.
        FlowDefinition flow = Pipelite.defineFlow("reused-flow")
            .fromSource("reused-in")
            .process("double", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) * 2))
            .toSink("reused-out")
            .build();

        given(flowDefinition(flow), inputPayload(5))
            .when(supplyTo("reused-in"))
            .then(output(isExecutionCompleted()), step("double", payloadEquals(10)));

        given(flowDefinition(flow), inputPayload(7))
            .when(supplyTo("reused-in"))
            .then(output(isExecutionCompleted()), step("double", payloadEquals(14)));
    }

    @Test(expected = AssertionError.class)
    public void givenProcessorThatThrows_whenSupplyTo_thenAssertionErrorWithCause() {
        // Regression test for Finding #8: a RuntimeException thrown inside a processor
        // previously caused the CompletableFuture to time out silently, giving no diagnostic.
        // The injected ExceptionHandler now records the exception so the timeout catch can
        // rethrow it as an AssertionError with the original message as cause.
        FlowDefinition flow = Pipelite.defineFlow("exception-flow")
            .fromSource("exc-in")
            .process("fail", (io, c) -> { throw new RuntimeException("simulated processor failure"); })
            .toSink("exc-out")
            .build();

        given(
                flowDefinition(flow),
                inputPayload("trigger"))
            .when(supplyTo("exc-in"));
    }

    @Test(expected = AssertionError.class)
    public void givenStepNotReached_whenInspectStep_thenThrowsAssertionError() {
        FlowDefinition flow = Pipelite.defineFlow("unreached-step-flow")
            .fromSource("in")
            .process("gate", (io, c) -> c.stopExecution())
            .process("never-reached", (io, c) -> { /* unreachable */ })
            .toSink("out")
            .build();

        given(
                flowDefinition(flow),
                timeout(1),
                inputPayload("x"))
            .when(supplyTo("in"))
            .then(step("never-reached", hasHeader("anything")));
    }

    // -------------------------------------------------------------------------
    // Finding #11 — duplicate step names across linked flows
    // -------------------------------------------------------------------------

    @Test
    public void givenTwoFlowsWithSameStepName_whenDisambiguatedByFlowName_thenCorrectSnapshotUsed() {
        // Regression test for Finding #11: two flows sharing a step name "transform"
        // previously overwrote each other's snapshot silently. Disambiguation via
        // step(flowName, stepName) must route to the correct snapshot.
        FlowDefinition flowA = Pipelite.defineFlow("flow-a")
            .fromSource("a-in")
            .process("transform", (io, c) -> io.setOutputPayload("A"))
            .toSink("link://b-in")
            .build();

        FlowDefinition flowB = Pipelite.defineFlow("flow-b")
            .fromSource("b-in")
            .process("transform", (io, c) -> io.setOutputPayload("B"))
            .toSink("b-out")
            .build();

        given(
                flowDefinition(flowA),
                flowDefinition(flowB),
                inputPayload("start"))
            .when(supplyTo("a-in"))
            .then(
                output(isExecutionCompleted()),
                step("flow-a", "transform", payloadEquals("A")),
                step("flow-b", "transform", payloadEquals("B")));
    }

    @Test(expected = AssertionError.class)
    public void givenTwoFlowsWithSameStepName_whenStepReferenceIsAmbiguous_thenAssertionError() {
        // step("transform") without a flow name is ambiguous when two flows each
        // have a step named "transform" — must throw AssertionError.
        FlowDefinition flowA = Pipelite.defineFlow("flow-amb-a")
            .fromSource("amb-a-in")
            .process("transform", (io, c) -> io.setOutputPayload("A"))
            .toSink("link://amb-b-in")
            .build();

        FlowDefinition flowB = Pipelite.defineFlow("flow-amb-b")
            .fromSource("amb-b-in")
            .process("transform", (io, c) -> io.setOutputPayload("B"))
            .toSink("amb-b-out")
            .build();

        given(
                flowDefinition(flowA),
                flowDefinition(flowB),
                inputPayload("start"))
            .when(supplyTo("amb-a-in"))
            .then(step("transform", payloadEquals("A")));
    }

    // -------------------------------------------------------------------------
    // Unknown entry-point endpoint
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void givenNoFlowMatchesEntryPoint_whenSupplyTo_thenThrowsIllegalArgumentException() {
        FlowDefinition flow = Pipelite.defineFlow("known-flow")
            .fromSource("known-in")
            .toSink("known-out")
            .build();

        given(flowDefinition(flow), inputPayload("x"))
            .when(supplyTo("totally-unknown-endpoint"));
    }

    // -------------------------------------------------------------------------
    // Flow with no sink (e.g. a recipient-list sub-flow)
    // -------------------------------------------------------------------------

    @Test
    public void givenFlowWithNoSink_whenSupplyTo_thenNeverCompletesAndIsNotExecutionCompleted() {
        FlowDefinition flow = Pipelite.defineFlow("no-sink-flow")
            .fromSource("no-sink-in")
            .process("step", (io, c) -> io.setOutputPayload("processed"))
            .build();

        given(flowDefinition(flow), timeout(1), inputPayload("x"))
            .when(supplyTo("no-sink-in"))
            .then(isNotExecutionCompleted());
    }

    // -------------------------------------------------------------------------
    // Duplicate flow names across two registered FlowDefinitions
    // -------------------------------------------------------------------------

    @Test(expected = DuplicateFlowDefinitionException.class)
    public void givenTwoFlowDefinitionsWithSameName_whenSupplyTo_thenThrowsDuplicateFlowDefinitionException() {
        FlowDefinition flowOne = Pipelite.defineFlow("duplicate-name-flow")
            .fromSource("dup-in-1")
            .toSink("dup-out-1")
            .build();

        FlowDefinition flowTwo = Pipelite.defineFlow("duplicate-name-flow")
            .fromSource("dup-in-2")
            .toSink("dup-out-2")
            .build();

        given(
                flowDefinition(flowOne),
                flowDefinition(flowTwo),
                inputPayload("x"))
            .when(supplyTo("dup-in-1"));
    }

    // -------------------------------------------------------------------------
    // Concurrency — two supplyTo() calls in parallel threads stay isolated
    // -------------------------------------------------------------------------

    @Test
    public void givenTwoIndependentFlows_whenSuppliedFromParallelThreads_thenEachCaptureStaysIsolated() throws Exception {
        FlowDefinition flowOne = Pipelite.defineFlow("concurrent-flow-1")
            .fromSource("concurrent-in-1")
            .process("tag", (io, c) -> io.setOutputPayload("one-" + io.getInputPayloadAs(String.class)))
            .toSink("concurrent-out-1")
            .build();

        FlowDefinition flowTwo = Pipelite.defineFlow("concurrent-flow-2")
            .fromSource("concurrent-in-2")
            .process("tag", (io, c) -> io.setOutputPayload("two-" + io.getInputPayloadAs(String.class)))
            .toSink("concurrent-out-2")
            .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> resultOne = executor.submit(() -> {
                ThenOperations then = given(flowDefinition(flowOne), inputPayload("payload"))
                    .when(supplyTo("concurrent-in-1"))
                    .then(isExecutionCompleted());
                return then.getOutputPayloadAs(String.class);
            });
            Future<String> resultTwo = executor.submit(() -> {
                ThenOperations then = given(flowDefinition(flowTwo), inputPayload("payload"))
                    .when(supplyTo("concurrent-in-2"))
                    .then(isExecutionCompleted());
                return then.getOutputPayloadAs(String.class);
            });

            Assert.assertEquals("one-payload", resultOne.get(10, TimeUnit.SECONDS));
            Assert.assertEquals("two-payload", resultTwo.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }
}
