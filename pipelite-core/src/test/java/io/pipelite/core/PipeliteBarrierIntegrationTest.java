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
package io.pipelite.core;

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.barrier.Release;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Issue #150, the use case it was written for: an order is handled by three flows that run in parallel, and a
 * fourth step starts only when all three have finished it. Written with the barrier, with no counting class of
 * the user's and no {@code contribution.stopExecution()}.
 */
public class PipeliteBarrierIntegrationTest {

    private record StepResult(String orderId, String step, boolean ok) implements Serializable {
    }

    private PipeliteContext context;

    // The barriers are on Pipelite Home, which outlives a test run: an order id that a previous run already
    // used would meet its state, so every run uses its own.
    private final String run = UUID.randomUUID().toString().substring(0, 8);

    @Before
    public void setup() {
        context = Pipelite.createContext();
    }

    @After
    public void tearDown() {
        context.stop();
    }

    /** A flow that does the work of one step for an order and reports the result to the queue of the barrier. */
    private void contributor(String step) {
        context.registerFlowDefinition(Pipelite.defineFlow("step-" + step)
            .fromSource("queue://order-" + step)
            .process("do-" + step, (exchange, contribution) ->
                exchange.setOutputPayload(new StepResult(exchange.getInputPayloadAs(String.class), step, true)))
            .toSink("queue://step-results")
            .build());
    }

    @Test
    public void givenThreeParallelFlows_thenTheNextStepRunsOncePerOrderAfterAllThreeFinished() {

        final Map<String, List<String>> prepared = new ConcurrentHashMap<>();
        contributor("record");
        contributor("check");
        contributor("plan");
        context.registerFlowDefinition(Pipelite.defineFlow("preparation")
            .fromSource("queue://step-results")
            .barrier("all-steps", b -> b
                .correlateBy(c -> c.payloadAttribute(StepResult::orderId))
                .expect("record", "check", "plan")
                .contributorBy(ex -> ex.getInputPayloadAs(StepResult.class).step())
                .release(Release.all())
                .timeout(Duration.ofMinutes(10)))
            .process("next-step", (exchange, contribution) -> {
                final List<?> results = exchange.getInputPayloadAs(List.class);
                final String orderId = ((StepResult) results.get(0)).orderId();
                prepared.computeIfAbsent(orderId, id -> new CopyOnWriteArrayList<>())
                    .addAll(results.stream().map(r -> ((StepResult) r).step()).toList());
            })
            .build());
        context.start();

        for (int order = 1; order <= 5; order++) {
            for (String step : List.of("record", "check", "plan")) {
                context.supplyExchange("queue://order-" + step, context.createExchange("order-" + run + "-" + order));
            }
        }

        Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> prepared.size() == 5);
        for (List<String> steps : prepared.values()) {
            Assert.assertEquals("one release per order, with the three steps", 3, steps.size());
            Assert.assertTrue(steps.containsAll(List.of("record", "check", "plan")));
        }
    }

    @Test
    public void givenOneStepNeverReports_thenTheOrderDoesNotGoOn() throws Exception {

        final List<String> released = new CopyOnWriteArrayList<>();
        contributor("record");
        contributor("check");
        context.registerFlowDefinition(Pipelite.defineFlow("preparation")
            .fromSource("queue://step-results")
            .barrier("both-steps", b -> b
                .correlateBy(c -> c.payloadAttribute(StepResult::orderId))
                .expect(2)
                .timeout(Duration.ofMinutes(10)))
            .process("next-step", (exchange, contribution) -> released.add("released"))
            .build());
        context.start();

        context.supplyExchange("queue://order-record", context.createExchange("order-" + run + "-1"));
        Thread.sleep(500);

        Assert.assertTrue("an order with one step missing waits", released.isEmpty());
    }

    @Test
    public void givenABarrierThatCouldNeverWork_thenTheFlowIsRefusedWhenItIsDefined() {
        try {
            Pipelite.defineFlow("preparation")
                .fromSource("queue://step-results")
                .barrier("incomplete", b -> b.correlateBy(c -> c.headerName("order-id")));
            Assert.fail("a barrier without expect and timeout must be refused");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("expect"));
        }
    }
}
