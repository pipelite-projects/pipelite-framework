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

import io.pipelite.core.context.ConfigurablePipeliteContext;
import io.pipelite.core.context.ContextValidationException;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.dsl.definition.FlowDefinition;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Issue #116, two layers of the same fix:
 * <p>
 * The built-in retry channel used to be created only if {@code
 * flowRegistry.isRegistered("retry-channel")} was false - a check on the resource of a source, not
 * on whether the retry channel itself had been created. {@code DefaultPipeliteContext} now tracks
 * that with its own field instead, so a collision on a protocol {@code ReservedFlowNameValidator}
 * does not check (below) no longer silently keeps the retry channel from being created.
 * <p>
 * {@code ReservedFlowNameValidator} additionally fails {@code start()} outright when a flow's own
 * name, or the queue its source reads, is exactly {@code retry-channel} - the two axes {@code
 * flowRegistry} keys its two maps by. A source resource shared with the retry channel under any
 * other protocol is not one of them: a resource is an address, never an identity (issue #108), and
 * the retry channel is never reached through one regardless (its source is a {@code
 * TypedSourceDefinition}, not a queue).
 */
public class PipeliteRetryChannelCollisionIntegrationTest {

    private ConfigurablePipeliteContext context;

    @After
    public void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    /**
     * @param collidingFlow      an extra flow to register alongside the retryable one, or
     *                           {@code null} for the baseline
     * @param collidingFlowFirst whether it is registered before the retryable flow
     * @return how many times the retryable flow's failing step ran
     */
    private int attemptsAfterOneFailure(FlowDefinition collidingFlow, boolean collidingFlowFirst) {
        context = (ConfigurablePipeliteContext) Pipelite.createContext();
        context.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());

        final AtomicInteger attempts = new AtomicInteger();
        final FlowDefinition retryable = Pipelite.defineFlow("retryable-flow")
            .fromSource("queue://orders")
            .process("flaky", (io, c) -> {
                if (attempts.incrementAndGet() == 1) {
                    throw new RuntimeException("first attempt fails");
                }
            })
            .withRetry(retry -> retry.maxAttempts(3).onErrorChannel(err -> err.toDLQ()))
            .build();

        if (collidingFlow != null && collidingFlowFirst) {
            context.registerFlowDefinition(collidingFlow);
        }
        context.registerFlowDefinition(retryable);
        if (collidingFlow != null && !collidingFlowFirst) {
            context.registerFlowDefinition(collidingFlow);
        }

        context.start();
        context.supplyExchange("queue://orders", context.getExchangeFactory().createExchange("x"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> attempts.get() >= 2);
        return attempts.get();
    }

    @Test
    public void baseline_noCollidingFlow_theRetryRuns() {
        Assert.assertEquals(2, attemptsAfterOneFailure(null, false));
    }

    /**
     * The mechanical fix on its own: a source resource shared under a protocol the reserved-name
     * validator does not check (below) is still legitimate, and must not keep the retry channel
     * from being created - regardless of whether it is registered before or after the retryable
     * flow, which is what made the old, registry-based check depend on registration order.
     */
    @Test
    public void givenATimeFlowNamedRetryChannel_registeredBeforeTheRetryableFlow_theRetryStillRuns() {
        final FlowDefinition collidingFlow = Pipelite.defineFlow("time-flow")
            .fromSource("time://retry-channel?period=3600000&timeUnit=MILLISECONDS")
            .build();
        Assert.assertEquals(2, attemptsAfterOneFailure(collidingFlow, true));
    }

    /**
     * The reserved-name validator: a queue named {@code retry-channel} is rejected at {@code
     * start()}, whichever side of the retryable flow it is registered on - unlike the old,
     * registry-based check, this one does not depend on registration order.
     */
    @Test
    public void givenAQueueFlowNamedRetryChannel_registeredBeforeTheRetryableFlow_thenStartFailsNamingIt() {
        final FlowDefinition collidingFlow = Pipelite.defineFlow("user-flow").fromSource("queue://retry-channel").build();
        assertStartFailsOn(collidingFlow, true,
            "Flow 'user-flow', fromSource(\"queue://retry-channel\"): 'retry-channel' is reserved for the framework's own retry channel; choose a different queue name");
    }

    @Test
    public void givenAQueueFlowNamedRetryChannel_registeredAfterTheRetryableFlow_thenStartFailsNamingIt() {
        final FlowDefinition collidingFlow = Pipelite.defineFlow("user-flow").fromSource("queue://retry-channel").build();
        assertStartFailsOn(collidingFlow, false,
            "Flow 'user-flow', fromSource(\"queue://retry-channel\"): 'retry-channel' is reserved for the framework's own retry channel; choose a different queue name");
    }

    /**
     * The other axis the validator checks: the flow's own {@code defineFlow(...)} name, an
     * identity distinct from the queue its source reads (issue #111) - reserved just the same.
     */
    @Test
    public void givenAFlowNamedRetryChannelOnAnotherSource_thenStartFailsNamingIt() {
        final FlowDefinition collidingFlow = Pipelite.defineFlow("retry-channel").fromSource("queue://something-else").build();
        assertStartFailsOn(collidingFlow, true,
            "Flow 'retry-channel', defineFlow(\"retry-channel\"): this name is reserved for the framework's own retry channel; choose a different flow name");
    }

    private void assertStartFailsOn(FlowDefinition collidingFlow, boolean collidingFlowFirst, String expectedProblem) {
        context = (ConfigurablePipeliteContext) Pipelite.createContext();
        context.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());

        final FlowDefinition retryable = Pipelite.defineFlow("retryable-flow")
            .fromSource("queue://orders")
            .withRetry(retry -> retry.maxAttempts(3).onErrorChannel(err -> err.toDLQ()))
            .build();

        if (collidingFlowFirst) {
            context.registerFlowDefinition(collidingFlow);
        }
        context.registerFlowDefinition(retryable);
        if (!collidingFlowFirst) {
            context.registerFlowDefinition(collidingFlow);
        }

        try {
            context.start();
            Assert.fail("expected the context not to start");
        } catch (ContextValidationException expected) {
            Assert.assertEquals(List.of(expectedProblem), expected.getProblems());
        }
    }

}
