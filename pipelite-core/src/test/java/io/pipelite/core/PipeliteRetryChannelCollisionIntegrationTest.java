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
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.dsl.definition.FlowDefinition;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Issue #116: the built-in retry channel used to be created only if {@code
 * flowRegistry.isRegistered("retry-channel")} was false - a check on the resource of a source, not
 * on whether the retry channel itself had been created. A user flow reading a source whose
 * resource happens to be {@code retry-channel}, registered before the first retryable flow, made
 * that check answer "yes, already registered" and silently skipped creating it: retries then never
 * ran, with no error. The fix tracks creation with its own field instead of asking the registry.
 * <p>
 * Every scenario below is expected to retry exactly like the baseline: a source resource is an
 * address, never an identity (issue #108), so a flow sharing {@code retry-channel}'s resource,
 * under any protocol, is legitimate and must not interfere.
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
        org.junit.Assert.assertEquals(2, attemptsAfterOneFailure(null, false));
    }

    @Test
    public void givenAQueueFlowNamedRetryChannel_registeredBeforeTheRetryableFlow_theRetryStillRuns() {
        final FlowDefinition collidingFlow = Pipelite.defineFlow("user-flow").fromSource("queue://retry-channel").build();
        org.junit.Assert.assertEquals(2, attemptsAfterOneFailure(collidingFlow, true));
    }

    @Test
    public void givenAQueueFlowNamedRetryChannel_registeredAfterTheRetryableFlow_theRetryRuns() {
        final FlowDefinition collidingFlow = Pipelite.defineFlow("user-flow").fromSource("queue://retry-channel").build();
        org.junit.Assert.assertEquals(2, attemptsAfterOneFailure(collidingFlow, false));
    }

    @Test
    public void givenATimeFlowNamedRetryChannel_registeredBeforeTheRetryableFlow_theRetryStillRuns() {
        final FlowDefinition collidingFlow = Pipelite.defineFlow("time-flow")
            .fromSource("time://retry-channel?period=3600000&timeUnit=MILLISECONDS")
            .build();
        org.junit.Assert.assertEquals(2, attemptsAfterOneFailure(collidingFlow, true));
    }

    @Test
    public void givenAFlowNamedRetryChannelOnAnotherSource_registeredBeforeTheRetryableFlow_theRetryRuns() {
        final FlowDefinition collidingFlow = Pipelite.defineFlow("retry-channel").fromSource("queue://something-else").build();
        org.junit.Assert.assertEquals(2, attemptsAfterOneFailure(collidingFlow, true));
    }

}
