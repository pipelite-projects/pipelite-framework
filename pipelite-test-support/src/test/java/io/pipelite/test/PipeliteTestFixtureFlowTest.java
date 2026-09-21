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
            .fromSource("queue://in")
            .process("enrich", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(String.class) + "-enriched"))
            .toSink("kafka://orders-out")
            .build();

        given(
                flowDefinition(flow),
                inputPayload("order"))
            .when(supplyTo("queue://in"))
            .then(isExecutionCompleted(), payloadEquals("order-enriched"));
    }

    // -------------------------------------------------------------------------
    // The entry point is a URL, like every source and destination (issue #111)
    // -------------------------------------------------------------------------

    @Test
    public void givenABareNameAsEntryPoint_whenSupplyTo_thenItIsRejectedSuggestingTheQueue() {
        FlowDefinition flow = Pipelite.defineFlow("bare-entry-flow")
            .fromSource("queue://in")
            .build();

        try {
            given(
                    flowDefinition(flow),
                    inputPayload("hello"))
                .when(supplyTo("in"));
            Assert.fail("expected a bare name not to be an entry point");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("write 'queue://in'"));
        }
    }

    // -------------------------------------------------------------------------
    // isCompleted / basic lifecycle
    // -------------------------------------------------------------------------

    @Test
    public void givenFlowWithNoProcessor_whenSupplyTo_thenFlowCompletes() {
        FlowDefinition flow = Pipelite.defineFlow("no-op-flow")
            .fromSource("queue://in")
            .build();

        given(
                flowDefinition(flow),
                inputPayload("hello"))
            .when(supplyTo("queue://in"))
            .then(isExecutionCompleted());
    }

    @Test
    public void givenFlowWithNoProcessor_whenSupplyTo_thenInputPayloadPassesThrough() {
        FlowDefinition flow = Pipelite.defineFlow("passthrough-flow")
            .fromSource("queue://in")
            .build();

        given(
                flowDefinition(flow),
                inputPayload("original"))
            .when(supplyTo("queue://in"))
            .then(payloadEquals("original"));
    }

    // -------------------------------------------------------------------------
    // Payload transformation
    // -------------------------------------------------------------------------

    @Test
    public void givenProcessorThatTransformsPayload_whenSupplyTo_thenOutputPayloadIsTransformed() {
        FlowDefinition flow = Pipelite.defineFlow("transform-flow")
            .fromSource("queue://in")
            .process("uppercase", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(String.class).toUpperCase()))
            .build();

        given(
                flowDefinition(flow),
                inputPayload("hello"))
            .when(supplyTo("queue://in"))
            .then(isExecutionCompleted(), payloadEquals("HELLO"));
    }

    @Test
    public void givenMultipleProcessors_whenSupplyTo_thenTransformationsAreChained() {
        FlowDefinition flow = Pipelite.defineFlow("chain-flow")
            .fromSource("queue://in")
            .process("step1", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(Integer.class) * 2))
            .process("step2", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(Integer.class) + 10))
            .build();

        given(
                flowDefinition(flow),
                inputPayload(5))
            .when(supplyTo("queue://in"))
            .then(isExecutionCompleted(), payloadEquals(20)); // (5*2)+10
    }

    @Test
    public void givenProcessorWithMapPayload_whenSupplyTo_thenGetOutputPayloadAsReturnsTypedMap() {
        Map<String, Object> transformed = new HashMap<>();
        transformed.put("price", 122.0);

        FlowDefinition flow = Pipelite.defineFlow("map-flow")
            .fromSource("queue://in")
            .process("enrich-price", (io, c) -> io.setOutputPayload(transformed))
            .build();

        ThenOperations then = given(
                flowDefinition(flow),
                inputPayload(Map.of("price", 100)))
            .when(supplyTo("queue://in"))
            .then(payloadEquals(transformed));

        then.getOutputPayloadAs(Map.class);
    }

    // -------------------------------------------------------------------------
    // Header propagation and mutation
    // -------------------------------------------------------------------------

    @Test
    public void givenInputHeaders_whenSupplyTo_thenHeadersArePropagatedToResult() {
        FlowDefinition flow = Pipelite.defineFlow("header-flow")
            .fromSource("queue://in")
            .process("noop", (io, c) -> io.setOutputPayload(io.getInputPayload()))
            .build();

        given(
                flowDefinition(flow),
                header("X-Tenant", "acme"),
                header("X-Correlation-Id", "abc-123"),
                inputPayload("data"))
            .when(supplyTo("queue://in"))
            .then(
                isExecutionCompleted(),
                headerEquals("X-Tenant", "acme"),
                headerEquals("X-Correlation-Id", "abc-123"));
    }

    @Test
    public void givenProcessorThatAddsHeader_whenSupplyTo_thenNewHeaderIsVisibleInResult() {
        FlowDefinition flow = Pipelite.defineFlow("add-header-flow")
            .fromSource("queue://in")
            .process("tag", (io, c) -> io.putHeader("X-Processed-By", "test-engine"))
            .build();

        given(
                flowDefinition(flow),
                inputPayload("data"))
            .when(supplyTo("queue://in"))
            .then(headerEquals("X-Processed-By", "test-engine"));
    }

    @Test
    public void givenIntegerHeader_whenGetHeaderAs_thenReturnsTypedValue() {
        FlowDefinition flow = Pipelite.defineFlow("typed-header-flow")
            .fromSource("queue://in")
            .build();

        given(
                flowDefinition(flow),
                header("Retry-Count", 3),
                inputPayload("data"))
            .when(supplyTo("queue://in"))
            .then(headerEquals("Retry-Count", 3));
    }

    @Test
    public void givenAbsentHeader_whenGetHeaderAs_thenReturnsNull() {
        FlowDefinition flow = Pipelite.defineFlow("no-header-flow")
            .fromSource("queue://in")
            .build();

        given(
                flowDefinition(flow),
                inputPayload("data"))
            .when(supplyTo("queue://in"))
            .then(noHeader("X-Missing"));
    }

    // -------------------------------------------------------------------------
    // Filtered / stopped execution
    // -------------------------------------------------------------------------

    @Test
    public void givenProcessorCallsStopExecution_whenSupplyTo_thenSinkIsNotReachedAndNotCompleted() {
        FlowDefinition flow = Pipelite.defineFlow("filter-flow")
            .fromSource("queue://in")
            .process("gate", (io, c) -> c.stopExecution())
            .build();

        given(
                flowDefinition(flow),
                timeout(1),
                inputPayload("filtered-message"))
            .when(supplyTo("queue://in"))
            .then(isNotExecutionCompleted());
    }

    @Test(expected = IllegalStateException.class)
    public void givenNotCompletedResult_whenGetOutputPayload_thenThrowsIllegalStateException() {
        FlowDefinition flow = Pipelite.defineFlow("filtered-no-inspect-flow")
            .fromSource("queue://in")
            .process("gate", (io, c) -> c.stopExecution())
            .build();

        ThenOperations then = given(
                flowDefinition(flow),
                timeout(1),
                inputPayload("x"))
            .when(supplyTo("queue://in"));

        then.getOutputPayload();
    }

    @Test(expected = AssertionError.class)
    public void givenFilteredFlow_whenIsExecutionCompletedAsserted_thenAssertionError() {
        FlowDefinition flow = Pipelite.defineFlow("filter-flow-wrong-assertion")
            .fromSource("queue://in-wrong-1")
            .process("gate", (io, c) -> c.stopExecution())
            .build();

        given(
                flowDefinition(flow),
                timeout(1),
                inputPayload("filtered-message"))
            .when(supplyTo("queue://in-wrong-1"))
            .then(isExecutionCompleted());
    }

    @Test(expected = AssertionError.class)
    public void givenCompletedFlow_whenIsNotExecutionCompletedAsserted_thenAssertionError() {
        FlowDefinition flow = Pipelite.defineFlow("completed-flow-wrong-assertion")
            .fromSource("queue://in-wrong-2")
            .build();

        given(
                flowDefinition(flow),
                inputPayload("hello"))
            .when(supplyTo("queue://in-wrong-2"))
            .then(isNotExecutionCompleted());
    }

    // -------------------------------------------------------------------------
    // Multi-flow (queue://)
    // -------------------------------------------------------------------------

    @Test
    public void givenTwoLinkedFlows_whenSupplyTo_thenBothFlowsExecuteAndCaptureSucceeds() {
        FlowDefinition originFlow = Pipelite.defineFlow("origin-flow")
            .fromSource("queue://entry")
            .process("enrich", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(String.class) + "-enriched"))
            .toSink("queue://destination-entry")
            .build();

        FlowDefinition destinationFlow = Pipelite.defineFlow("destination-flow")
            .fromSource("queue://destination-entry")
            .process("finalize", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(String.class) + "-finalized"))
            .build();

        given(
                flowDefinition(originFlow),
                flowDefinition(destinationFlow),
                inputPayload("msg"))
            .when(supplyTo("queue://entry"))
            .then(isExecutionCompleted(), payloadEquals("msg-enriched-finalized"));
    }

    @Test
    public void givenTwoLinkedFlowsWithRealSinkOnDestination_whenSupplyTo_thenLinkHopIsPreservedAndRealSinkIsCaptured() {
        // The queue:// hop between the two flows must be left untouched (otherwise the destination
        // flow would never receive the exchange), while only destinationFlow's real, unconfigured
        // "kafka://" sink gets transparently redirected to capture. step(...) assertions are also
        // verified on a step from EACH flow, proving step snapshots are captured across the whole chain.
        FlowDefinition originFlow = Pipelite.defineFlow("origin-flow-2")
            .fromSource("queue://entry-2")
            .process("enrich", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(String.class) + "-enriched"))
            .toSink("queue://destination-entry-2")
            .build();

        FlowDefinition destinationFlow = Pipelite.defineFlow("destination-flow-2")
            .fromSource("queue://destination-entry-2")
            .process("finalize", (io, c) -> io.setOutputPayload(
                io.getInputPayloadAs(String.class) + "-finalized"))
            .toSink("kafka://orders-out")
            .build();

        given(
                flowDefinition(originFlow),
                flowDefinition(destinationFlow),
                header("X-Trace-Id", "trace-42"),
                inputPayload("msg"))
            .when(supplyTo("queue://entry-2"))
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
            .fromSource("queue://in")
            .transformPayload("double-it",
                holder -> holder.getPayloadAs(Integer.class) * 2)
            .build();

        given(
                flowDefinition(flow),
                inputPayload(21))
            .when(supplyTo("queue://in"))
            .then(isExecutionCompleted(), payloadEquals(42));
    }

    // -------------------------------------------------------------------------
    // Step-by-step inspection
    // -------------------------------------------------------------------------

    @Test
    public void givenMultiStepFlow_whenInspectStep_thenIntermediateStateIsVisible() {
        FlowDefinition flow = Pipelite.defineFlow("inspect-flow")
            .fromSource("queue://in")
            .process("step1", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) * 2))
            .process("step2", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) + 10))
            .build();

        given(
                flowDefinition(flow),
                inputPayload(5))
            .when(supplyTo("queue://in"))
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
            .fromSource("queue://reused-in")
            .process("double", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) * 2))
            .build();

        given(flowDefinition(flow), inputPayload(5))
            .when(supplyTo("queue://reused-in"))
            .then(output(isExecutionCompleted()), step("double", payloadEquals(10)));

        given(flowDefinition(flow), inputPayload(7))
            .when(supplyTo("queue://reused-in"))
            .then(output(isExecutionCompleted()), step("double", payloadEquals(14)));
    }

    @Test(expected = AssertionError.class)
    public void givenProcessorThatThrows_whenSupplyTo_thenAssertionErrorWithCause() {
        // Regression test for Finding #8: a RuntimeException thrown inside a processor
        // previously caused the CompletableFuture to time out silently, giving no diagnostic.
        // The injected ExceptionHandler now records the exception so the timeout catch can
        // rethrow it as an AssertionError with the original message as cause.
        FlowDefinition flow = Pipelite.defineFlow("exception-flow")
            .fromSource("queue://exc-in")
            .process("fail", (io, c) -> { throw new RuntimeException("simulated processor failure"); })
            .build();

        given(
                flowDefinition(flow),
                inputPayload("trigger"))
            .when(supplyTo("queue://exc-in"));
    }

    @Test(expected = AssertionError.class)
    public void givenStepNotReached_whenInspectStep_thenThrowsAssertionError() {
        FlowDefinition flow = Pipelite.defineFlow("unreached-step-flow")
            .fromSource("queue://in")
            .process("gate", (io, c) -> c.stopExecution())
            .process("never-reached", (io, c) -> { /* unreachable */ })
            .build();

        given(
                flowDefinition(flow),
                timeout(1),
                inputPayload("x"))
            .when(supplyTo("queue://in"))
            .then(step("never-reached", hasHeader("anything")));
    }

    // -------------------------------------------------------------------------
    // Issue #27 — step snapshots must not share mutable Headers across steps
    // -------------------------------------------------------------------------

    @Test
    public void givenHeaderMutatedInLaterStep_whenInspectEarlierStepSnapshot_thenEarlierSnapshotIsUnaffected() {
        // Regression test for issue #27: DefaultExchangeFactory.copyExchange/nextExchange
        // previously shared the same mutable Headers instance across every Exchange derived
        // from a common ancestor. Since StepSnapshotCapture snapshots the Exchange via
        // copyExchange() right after each step, a header written in a LATER step used to
        // leak into the snapshot already taken for an EARLIER step.
        FlowDefinition flow = Pipelite.defineFlow("header-isolation-flow")
            .fromSource("queue://header-isolation-in")
            .process("first-step", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(String.class)))
            .process("second-step", (io, c) -> io.putHeader("X-Added-Later", "second-step-value"))
            .build();

        given(
                flowDefinition(flow),
                inputPayload("data"))
            .when(supplyTo("queue://header-isolation-in"))
            .then(
                output(isExecutionCompleted()),
                step("first-step", noHeader("X-Added-Later")),
                step("second-step", headerEquals("X-Added-Later", "second-step-value")));
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
            .fromSource("queue://a-in")
            .process("transform", (io, c) -> io.setOutputPayload("A"))
            .toSink("queue://b-in")
            .build();

        FlowDefinition flowB = Pipelite.defineFlow("flow-b")
            .fromSource("queue://b-in")
            .process("transform", (io, c) -> io.setOutputPayload("B"))
            .build();

        given(
                flowDefinition(flowA),
                flowDefinition(flowB),
                inputPayload("start"))
            .when(supplyTo("queue://a-in"))
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
            .fromSource("queue://amb-a-in")
            .process("transform", (io, c) -> io.setOutputPayload("A"))
            .toSink("queue://amb-b-in")
            .build();

        FlowDefinition flowB = Pipelite.defineFlow("flow-amb-b")
            .fromSource("queue://amb-b-in")
            .process("transform", (io, c) -> io.setOutputPayload("B"))
            .build();

        given(
                flowDefinition(flowA),
                flowDefinition(flowB),
                inputPayload("start"))
            .when(supplyTo("queue://amb-a-in"))
            .then(step("transform", payloadEquals("A")));
    }

    // -------------------------------------------------------------------------
    // Unknown entry-point endpoint
    // -------------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void givenNoFlowMatchesEntryPoint_whenSupplyTo_thenThrowsIllegalArgumentException() {
        FlowDefinition flow = Pipelite.defineFlow("known-flow")
            .fromSource("queue://known-in")
            .build();

        given(flowDefinition(flow), inputPayload("x"))
            .when(supplyTo("queue://totally-unknown-endpoint"));
    }

    // -------------------------------------------------------------------------
    // Flow with no sink: it ends after its last step, and that is what is captured (issue #112)
    // -------------------------------------------------------------------------

    @Test
    public void givenFlowWithNoSink_whenSupplyTo_thenTheEndOfItsLastStepIsCaptured() {
        FlowDefinition flow = Pipelite.defineFlow("no-sink-flow")
            .fromSource("queue://no-sink-in")
            .process("step", (io, c) -> io.setOutputPayload("processed"))
            .build();

        given(flowDefinition(flow), inputPayload("x"))
            .when(supplyTo("queue://no-sink-in"))
            .then(isExecutionCompleted(), payloadEquals("processed"));
    }

    @Test
    public void givenFlowWithNoSinkWhoseStepStopsTheExecution_whenSupplyTo_thenNothingIsCaptured() {
        FlowDefinition flow = Pipelite.defineFlow("filtered-no-sink-flow")
            .fromSource("queue://filtered-in")
            .filter("only-yes", "Headers['pass'] == 'yes'")
            .process("step", (io, c) -> io.setOutputPayload("processed"))
            .build();

        given(flowDefinition(flow), timeout(1), header("pass", "no"), inputPayload("x"))
            .when(supplyTo("queue://filtered-in"))
            .then(isNotExecutionCompleted());
    }

    @Test
    public void givenChainedFlowsWhoseLastHasNoSink_whenSupplyTo_thenTheEndOfTheLastFlowIsCaptured() {
        FlowDefinition first = Pipelite.defineFlow("first-flow")
            .fromSource("queue://first-in")
            .process("step-a", (io, c) -> io.setOutputPayload("from-first"))
            .toSink("queue://second-in")
            .build();
        FlowDefinition second = Pipelite.defineFlow("second-flow")
            .fromSource("queue://second-in")
            .process("step-b", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(String.class) + "+second"))
            .build();

        given(flowDefinition(first), flowDefinition(second), inputPayload("x"))
            .when(supplyTo("queue://first-in"))
            .then(isExecutionCompleted(), payloadEquals("from-first+second"));
    }

    /**
     * A flow whose own exit is a {@code toRoute(...)} has no end to capture: the exchange goes on
     * to another flow, and that one is where it ends.
     */
    @Test
    public void givenAFlowEndingInARouteToFlowsWithNoSink_whenSupplyTo_thenTheEndOfTheChosenFlowIsCaptured() {
        FlowDefinition router = Pipelite.defineFlow("router-flow")
            .fromSource("queue://router-in")
            .process("mark", (io, c) -> io.setOutputPayload("routed"))
            .toRoute(routes -> routes.dynamic()
                .when("Headers['route'] == 'b'").then("queue://route-b-in")
                .otherwise("queue://route-a-in")
                .end())
            .build();
        FlowDefinition routeA = Pipelite.defineFlow("route-a-flow")
            .fromSource("queue://route-a-in")
            .process("a", (io, c) -> io.setOutputPayload("from-a"))
            .build();
        FlowDefinition routeB = Pipelite.defineFlow("route-b-flow")
            .fromSource("queue://route-b-in")
            .process("b", (io, c) -> io.setOutputPayload("from-b"))
            .build();

        given(flowDefinition(router), flowDefinition(routeA), flowDefinition(routeB), header("route", "b"), inputPayload("x"))
            .when(supplyTo("queue://router-in"))
            .then(isExecutionCompleted(), payloadEquals("from-b"));
    }

    // -------------------------------------------------------------------------
    // Duplicate flow names across two registered FlowDefinitions
    // -------------------------------------------------------------------------

    @Test(expected = DuplicateFlowDefinitionException.class)
    public void givenTwoFlowDefinitionsWithSameName_whenSupplyTo_thenThrowsDuplicateFlowDefinitionException() {
        FlowDefinition flowOne = Pipelite.defineFlow("duplicate-name-flow")
            .fromSource("queue://dup-in-1")
            .build();

        FlowDefinition flowTwo = Pipelite.defineFlow("duplicate-name-flow")
            .fromSource("queue://dup-in-2")
            .build();

        given(
                flowDefinition(flowOne),
                flowDefinition(flowTwo),
                inputPayload("x"))
            .when(supplyTo("queue://dup-in-1"));
    }

    // -------------------------------------------------------------------------
    // Concurrency — two supplyTo() calls in parallel threads stay isolated
    // -------------------------------------------------------------------------

    @Test
    public void givenTwoIndependentFlows_whenSuppliedFromParallelThreads_thenEachCaptureStaysIsolated() throws Exception {
        FlowDefinition flowOne = Pipelite.defineFlow("concurrent-flow-1")
            .fromSource("queue://concurrent-in-1")
            .process("tag", (io, c) -> io.setOutputPayload("one-" + io.getInputPayloadAs(String.class)))
            .build();

        FlowDefinition flowTwo = Pipelite.defineFlow("concurrent-flow-2")
            .fromSource("queue://concurrent-in-2")
            .process("tag", (io, c) -> io.setOutputPayload("two-" + io.getInputPayloadAs(String.class)))
            .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> resultOne = executor.submit(() -> {
                ThenOperations then = given(flowDefinition(flowOne), inputPayload("payload"))
                    .when(supplyTo("queue://concurrent-in-1"))
                    .then(isExecutionCompleted());
                return then.getOutputPayloadAs(String.class);
            });
            Future<String> resultTwo = executor.submit(() -> {
                ThenOperations then = given(flowDefinition(flowTwo), inputPayload("payload"))
                    .when(supplyTo("queue://concurrent-in-2"))
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
