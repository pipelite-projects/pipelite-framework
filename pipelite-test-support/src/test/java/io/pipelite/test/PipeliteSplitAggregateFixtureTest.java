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
import org.junit.Test;

import java.util.List;

import static io.pipelite.test.PipeliteTest.*;

public class PipeliteSplitAggregateFixtureTest {

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    public void givenCollectionPayload_whenSplitAndProcessed_thenAggregatedPayloadPreservesOrder() {
        FlowDefinition flow = Pipelite.defineFlow("split-happy-path-flow")
            .fromSource("split-happy-in")
            .split("split-step", segment -> segment
                .process("double-it", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) * 2))
                .end())
            .toSink("split-happy-out")
            .build();

        given(
                flowDefinition(flow),
                inputPayload(List.of(1, 2, 3, 4)))
            .when(supplyTo("split-happy-in"))
            .then(
                isExecutionCompleted(),
                payloadEquals(List.of(2, 4, 6, 8)));
    }

    // -------------------------------------------------------------------------
    // Empty collection
    // -------------------------------------------------------------------------

    @Test
    public void givenEmptyCollectionPayload_whenSplit_thenAggregatedPayloadIsEmptyAndFlowCompletes() {
        FlowDefinition flow = Pipelite.defineFlow("split-empty-flow")
            .fromSource("split-empty-in")
            .split("split-step", segment -> segment
                .process("noop", (io, c) -> io.setOutputPayload(io.getInputPayload()))
                .end())
            .toSink("split-empty-out")
            .build();

        given(
                flowDefinition(flow),
                inputPayload(List.of()))
            .when(supplyTo("split-empty-in"))
            .then(isExecutionCompleted(), payloadEquals(List.of()));
    }

    // -------------------------------------------------------------------------
    // Zero-step segment (pure pass-through)
    // -------------------------------------------------------------------------

    @Test
    public void givenZeroStepSegment_whenSplit_thenAggregatedPayloadIsUnchangedItems() {
        FlowDefinition flow = Pipelite.defineFlow("split-zero-step-flow")
            .fromSource("split-zero-step-in")
            .split("split-step", segment -> segment.end())
            .toSink("split-zero-step-out")
            .build();

        given(
                flowDefinition(flow),
                inputPayload(List.of("a", "b", "c")))
            .when(supplyTo("split-zero-step-in"))
            .then(isExecutionCompleted(), payloadEquals(List.of("a", "b", "c")));
    }

    // -------------------------------------------------------------------------
    // Multi-step segment
    // -------------------------------------------------------------------------

    @Test
    public void givenMultiStepSegment_whenSplit_thenEachChildTraversesAllStepsInOrder() {
        FlowDefinition flow = Pipelite.defineFlow("split-multi-step-flow")
            .fromSource("split-multi-step-in")
            .split("split-step", segment -> segment
                .process("step-1", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) * 2))
                .process("step-2", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) + 1))
                .end())
            .toSink("split-multi-step-out")
            .build();

        given(
                flowDefinition(flow),
                inputPayload(List.of(1, 2, 3)))
            .when(supplyTo("split-multi-step-in"))
            .then(isExecutionCompleted(), payloadEquals(List.of(3, 5, 7))); // (n*2)+1
    }

    // -------------------------------------------------------------------------
    // Correction #8 regression: step(...) inspection on a step inside a segment
    // -------------------------------------------------------------------------

    @Test
    public void givenSplitFlow_whenInspectingInnerStep_thenStepSnapshotIsCapturedForLastProcessedChild() {
        FlowDefinition flow = Pipelite.defineFlow("split-step-inspection-flow")
            .fromSource("split-step-inspection-in")
            .split("split-step", segment -> segment
                .process("tag-it", (io, c) -> io.setOutputPayload("tagged-" + io.getInputPayloadAs(String.class)))
                .end())
            .toSink("split-step-inspection-out")
            .build();

        given(
                flowDefinition(flow),
                inputPayload(List.of("x", "y", "z")))
            .when(supplyTo("split-step-inspection-in"))
            .then(
                output(isExecutionCompleted(), payloadEquals(List.of("tagged-x", "tagged-y", "tagged-z"))),
                // Without propagating addExchangePostProcessor to inner segment nodes
                // (correction #8), no snapshot would ever be registered for "tag-it".
                // The snapshot reflects the LAST child processed ("z"), consistent with
                // the absence of per-item granularity in this iteration's scope.
                step("tag-it", payloadEquals("tagged-z")));
    }

    // -------------------------------------------------------------------------
    // Correction #5: aggregated Exchange identity/headers continuity
    // -------------------------------------------------------------------------

    @Test
    public void givenHeaderSetBeforeSplit_whenAggregated_thenHeaderIsPreservedOnNextNode() {
        FlowDefinition flow = Pipelite.defineFlow("split-header-continuity-flow")
            .fromSource("split-header-continuity-in")
            .split("split-step", segment -> segment
                .process("noop", (io, c) -> io.setOutputPayload(io.getInputPayload()))
                .end())
            .toSink("split-header-continuity-out")
            .build();

        given(
                flowDefinition(flow),
                header("X-Correlation-Id", "corr-42"),
                inputPayload(List.of(1, 2)))
            .when(supplyTo("split-header-continuity-in"))
            .then(
                isExecutionCompleted(),
                headerEquals("X-Correlation-Id", "corr-42"),
                payloadEquals(List.of(1, 2)));
    }
}
