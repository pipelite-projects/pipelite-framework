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
import io.pipelite.dsl.definition.builder.Backoff;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Issue #95: a retry declared with {@code .backoff(...)} waits between attempts instead of being
 * retried as soon as the retry channel next polls - {@code
 * PipeliteRetryChannelIntegrationTest}'s own baseline (no backoff configured) already covers the
 * pre-existing immediate-retry behavior, unchanged here.
 */
public class PipeliteRetryBackoffIntegrationTest {

    private ConfigurablePipeliteContext context;

    @After
    public void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    public void givenALinearBackoffIsConfigured_thenTheSecondAttemptWaitsAtLeastItsDelay() {

        final List<Long> attemptTimestamps = new CopyOnWriteArrayList<>();
        final FlowDefinition testFlow = Pipelite.defineFlow("backoff-flow")
            .fromSource("queue://ingress")
            .process("throw-once", (exchange, contribution) -> {
                attemptTimestamps.add(System.currentTimeMillis());
                if (attemptTimestamps.size() >= 2) {
                    contribution.stopExecution();
                    return;
                }
                throw new RuntimeException("Programmatic exception");
            })
            .withRetry(retry -> retry
                .maxAttempts(3)
                .backoff(Backoff.linear(Duration.ofSeconds(2)))
                .onErrorChannel(err -> err.toDLQ()))
            .build();

        context = (ConfigurablePipeliteContext) Pipelite.createContext();
        // Same reasoning as PipeliteRetryChannelIntegrationTest: only the timing between attempts
        // matters here, not persistence.
        context.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());
        context.registerFlowDefinition(testFlow);
        context.start();

        context.supplyExchange("queue://ingress", context.getExchangeFactory().createExchange("test-message"));

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> attemptTimestamps.size() >= 2);

        final long gapMillis = attemptTimestamps.get(1) - attemptTimestamps.get(0);
        Assert.assertTrue("expected at least the configured 2s backoff between attempts, got " + gapMillis + "ms",
            gapMillis >= 1900);
    }

}
