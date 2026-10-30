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
package io.pipelite.core.context.internal.validation;

import io.pipelite.core.Pipelite;
import io.pipelite.dsl.definition.FlowDefinition;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Issue #150: what a barrier awaits is checked when the context starts: that a flow reads the queue, that no flow
 * waits for its own completion, and that flows do not wait for each other in a cycle.
 */
public class BarrierReferenceValidatorTest {

    private static FlowDefinition plain(String name, String queue) {
        return Pipelite.defineFlow(name)
            .fromSource("queue://" + queue)
            .process("work", (exchange, contribution) -> { })
            .build();
    }

    private static FlowDefinition waiting(String name, String queue, String... awaited) {
        return Pipelite.defineFlow(name)
            .fromSource("queue://" + queue)
            .barrier("wait", b -> {
                for (String url : awaited) {
                    b.await(url, c -> c.headerName("order-id"));
                }
                return b.timeout(Duration.ofMinutes(5));
            })
            .process("work", (exchange, contribution) -> { })
            .build();
    }

    private static List<String> errorsOf(FlowDefinition... flows) {
        final ValidationContext context = new ValidationContext() {
            @Override
            public Collection<FlowDefinition> flowDefinitions() {
                return List.of(flows);
            }

            @Override
            public String resolveURL(String rawURL) {
                return rawURL;
            }
        };
        final ValidationReport report = new ValidationReport();
        new BarrierReferenceValidator().validate(context, report);
        return report.errors().stream().map(ValidationReport.Finding::describe).collect(Collectors.toList());
    }

    @Test
    public void givenEveryAwaitedFlowExists_thenThereIsNothingToReport() {
        Assert.assertTrue(errorsOf(
            waiting("a", "a-in", "queue://b-in", "queue://c-in"), plain("b", "b-in"), plain("c", "c-in")).isEmpty());
    }

    @Test
    public void givenAFlowThatAwaitsAChainOfFlows_thenItIsNotACycle() {
        Assert.assertTrue(errorsOf(
            waiting("a", "a-in", "queue://b-in"), waiting("b", "b-in", "queue://c-in"), plain("c", "c-in")).isEmpty());
    }

    @Test
    public void givenNoFlowReadsTheQueue_thenTheErrorNamesTheBarrierAndTheQueue() {
        final List<String> errors = errorsOf(waiting("a", "a-in", "queue://nobody-reads-this"));

        Assert.assertEquals(1, errors.size());
        Assert.assertTrue(errors.get(0), errors.get(0).contains("Flow 'a'"));
        Assert.assertTrue(errors.get(0), errors.get(0).contains("barrier 'wait'"));
        Assert.assertTrue(errors.get(0), errors.get(0).contains("nobody-reads-this"));
    }

    @Test
    public void givenAFlowThatAwaitsItself_thenItIsRefused() {
        final List<String> errors = errorsOf(waiting("a", "a-in", "queue://a-in"));

        Assert.assertEquals(1, errors.size());
        Assert.assertTrue(errors.get(0), errors.get(0).contains("its own completion"));
    }

    @Test
    public void givenTwoFlowsThatAwaitEachOther_thenTheCycleIsNamed() {
        final List<String> errors = errorsOf(waiting("a", "a-in", "queue://b-in"), waiting("b", "b-in", "queue://a-in"));

        Assert.assertEquals(1, errors.size());
        Assert.assertTrue(errors.get(0), errors.get(0).contains("a -> b -> a"));
    }

    @Test
    public void givenAThreeFlowCycle_thenItIsRefused() {
        final List<String> errors = errorsOf(
            waiting("a", "a-in", "queue://b-in"), waiting("b", "b-in", "queue://c-in"), waiting("c", "c-in", "queue://a-in"));

        Assert.assertEquals(1, errors.size());
        Assert.assertTrue(errors.get(0), errors.get(0).contains("a -> b -> c -> a"));
    }

    @Test
    public void givenTwoFlowsThatBothAwaitTheSameFlow_thenItIsNotACycle() {
        Assert.assertTrue(errorsOf(
            waiting("a", "a-in", "queue://c-in"), waiting("b", "b-in", "queue://c-in"), plain("c", "c-in")).isEmpty());
    }
}
