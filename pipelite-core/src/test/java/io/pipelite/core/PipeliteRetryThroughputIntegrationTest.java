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
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regression coverage for issue #61: the retry-channel used to drain exactly one
 * {@code FlowExecutionDump} per scheduling tick (default period 1s) regardless of backlog size -
 * ten simultaneously pending retries would take roughly ten seconds to clear even though nothing
 * else was contending for the single-threaded {@code RetryService} executor. Now bounded-batches
 * (default 50 per tick, see {@code RetryChannelDefinitionFactory#RETRY_BATCH_SIZE}), so the same
 * backlog clears within one or two ticks.
 */
public class PipeliteRetryThroughputIntegrationTest {

    @Test
    public void givenSeveralSimultaneousRetries_thenAllDrainWithinOneOrTwoTicksNotOnePerTick() {

        final ConfigurablePipeliteContext pipeliteContext = (ConfigurablePipeliteContext) Pipelite.createContext();
        // Retry-routing mechanics/timing only, not persistence - in-memory keeps this hermetic.
        pipeliteContext.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());

        final int messageCount = 10;
        final Set<String> failedOnce = ConcurrentHashMap.newKeySet();
        final AtomicInteger successCount = new AtomicInteger(0);

        final FlowDefinition testFlow = Pipelite.defineFlow("retry-throughput-flow")
            .fromSource("queue://retry-throughput-in")
            .process("fail-once-per-message", (io, c) -> {
                final String payload = io.getInputPayloadAs(String.class);
                if (failedOnce.add(payload)) {
                    throw new RuntimeException("simulated first-attempt failure for " + payload);
                }
                successCount.incrementAndGet();
            })
            .withRetry(retry -> retry.maxAttempts(5).onErrorChannel(err -> err.toDLQ()))
            .build();

        pipeliteContext.registerFlowDefinition(testFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        for (int i = 0; i < messageCount; i++) {
            pipeliteContext.supplyExchange("queue://retry-throughput-in", exchangeFactory.createExchange("message-" + i));
        }

        // At the old one-dump-per-tick rate this would need ~messageCount seconds (10s+) at
        // minimum; at up to 50/tick it clears within one or two 1s ticks. 5s leaves a wide,
        // non-flaky margin between the two while still failing fast against the old behavior.
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> successCount.get() == messageCount);
    }

}
