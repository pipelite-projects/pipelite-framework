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

import io.pipelite.dsl.split.SplitHeaders;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.pipelite.test.PipeliteTest.*;

/**
 * The elements of a split flow on as exchanges of their own (issue #149). The fixture captures the first exchange that
 * reaches the end of the flow, so what every element does is observed from the flow itself.
 */
public class PipeliteSplitFixtureTest {

    @Test
    public void givenCollectionPayload_whenSplit_thenEveryElementGoesThroughTheStepsThatFollowInOrder() {
        final List<Integer> doubled = new CopyOnWriteArrayList<>();
        FlowDefinition flow = Pipelite.defineFlow("split-happy-path-flow")
            .fromSource("queue://split-happy-in")
            .split("split-step")
            .process("double-it", (io, c) -> {
                io.setOutputPayload(io.getInputPayloadAs(Integer.class) * 2);
                doubled.add(io.getInputPayloadAs(Integer.class) * 2);
            })
            .build();

        given(
                flowDefinition(flow),
                inputPayload(List.of(1, 2, 3, 4)))
            .when(supplyTo("queue://split-happy-in"))
            .then(
                isExecutionCompleted(),
                payloadEquals(2),
                headerEquals(SplitHeaders.INDEX, 0),
                headerEquals(SplitHeaders.SIZE, 4));

        org.junit.Assert.assertEquals(List.of(2, 4, 6, 8), doubled);
    }

    @Test
    public void givenMultipleSteps_whenSplit_thenEachElementTraversesAllOfThemInOrder() {
        final List<Integer> results = new CopyOnWriteArrayList<>();
        FlowDefinition flow = Pipelite.defineFlow("split-multi-step-flow")
            .fromSource("queue://split-multi-step-in")
            .split("split-step")
            .process("step-1", (io, c) -> io.setOutputPayload(io.getInputPayloadAs(Integer.class) * 2))
            .process("step-2", (io, c) -> {
                io.setOutputPayload(io.getInputPayloadAs(Integer.class) + 1);
                results.add(io.getInputPayloadAs(Integer.class) + 1);
            })
            .build();

        given(
                flowDefinition(flow),
                inputPayload(List.of(1, 2, 3)))
            .when(supplyTo("queue://split-multi-step-in"))
            .then(isExecutionCompleted(), payloadEquals(3));

        org.junit.Assert.assertEquals(List.of(3, 5, 7), results); // (n*2)+1
    }

    @Test
    public void givenSplitFlow_whenInspectingAStepAfterIt_thenTheSnapshotIsOfAnElementOfTheSplit() {
        FlowDefinition flow = Pipelite.defineFlow("split-step-inspection-flow")
            .fromSource("queue://split-step-inspection-in")
            .split("split-step")
            .process("tag-it", (io, c) -> io.setOutputPayload("tagged-" + io.getInputPayloadAs(String.class)))
            .build();

        given(
                flowDefinition(flow),
                inputPayload(List.of("x", "y", "z")))
            .when(supplyTo("queue://split-step-inspection-in"))
            .then(
                output(isExecutionCompleted(), payloadEquals("tagged-x")),
                // A snapshot is taken each time the step runs, and the fixture stops taking them once the first
                // exchange has reached the end, so which element it holds is not for a test to rely on: what every
                // element has in common is.
                step("tag-it", headerEquals(SplitHeaders.SIZE, 3)));
    }

    @Test
    public void givenHeaderSetBeforeSplit_whenSplit_thenEveryElementHasItAndTheHeadersOfTheSplit() {
        FlowDefinition flow = Pipelite.defineFlow("split-header-continuity-flow")
            .fromSource("queue://split-header-continuity-in")
            .split("split-step")
            .process("noop", (io, c) -> io.setOutputPayload(io.getInputPayload()))
            .build();

        given(
                flowDefinition(flow),
                header("X-Correlation-Id", "corr-42"),
                inputPayload(List.of(1, 2)))
            .when(supplyTo("queue://split-header-continuity-in"))
            .then(
                isExecutionCompleted(),
                headerEquals("X-Correlation-Id", "corr-42"),
                hasHeader(SplitHeaders.ID),
                headerEquals(SplitHeaders.SIZE, 2));
    }
}
