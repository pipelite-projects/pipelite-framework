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

import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.flow.barrier.BarrierFileRepository;
import io.pipelite.core.flow.execution.dump.FileFlowExecutionDumpRepository;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.definition.SourceConcurrencyConfigurer;
import io.pipelite.dsl.definition.builder.Backoff;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Issue #156, across a restart: an exchange that was waiting for its retry, or at a barrier, still holds its key when
 * the process starts again, so the messages that arrive first, the ones the inbox gives back, do not overtake it.
 */
public class PipeliteOrderedConcurrencyRestartIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private DefaultPipeliteContext context;
    private Path dumps;
    private Path barriers;
    private final List<String> trace = new CopyOnWriteArrayList<>();
    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();

    @Before
    public void setup() {
        dumps = temporaryFolder.getRoot().toPath().resolve("retry");
        barriers = temporaryFolder.getRoot().toPath().resolve("barrier");
    }

    @After
    public void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    /** A context on the same folders every time, as the process that starts again has. */
    private DefaultPipeliteContext newContext() {
        final DefaultPipeliteContext created = new DefaultPipeliteContext();
        created.setFlowExecutionDumpRepository(new FileFlowExecutionDumpRepository(dumps));
        created.setBarrierRepository(new BarrierFileRepository(barriers));
        created.setDeadLetterQueueRepository(entry -> { });
        return created;
    }

    private void supply(String customer, String payload) {
        final Exchange exchange = context.createExchange(payload);
        exchange.putHeader("customer-id", customer);
        context.supplyExchange("queue://customer-updates", exchange);
    }

    private List<String> updated() {
        final List<String> result = new ArrayList<>();
        for (String entry : trace) {
            if (entry.startsWith("update:")) {
                result.add(entry.substring("update:".length()));
            }
        }
        return result;
    }

    private void registerWithRetry() {
        context.registerFlowDefinition(Pipelite.defineFlow("customer-updates")
            .fromSource("queue://customer-updates", (SourceConcurrencyConfigurer c) -> {
                c.durableInbox(false);
                c.concurrency(4).orderedBy(k -> k.headerName("customer-id"));
            })
            .process("update", (exchange, contribution) -> {
                final String payload = exchange.getInputPayloadAs(String.class);
                if ("A1".equals(payload) && attempts.computeIfAbsent(payload, key -> new AtomicInteger()).incrementAndGet() == 1) {
                    throw new IllegalStateException("simulated failure of A1");
                }
                trace.add("update:" + payload);
            })
            .withRetry(retry -> retry
                .maxAttempts(3)
                .backoff(Backoff.linear(Duration.ofSeconds(4)))
                .onErrorChannel(err -> err.toDLQ()))
            .build());
    }

    @Test
    public void givenAnExchangeWasWaitingForItsRetryWhenTheProcessStopped_thenTheMessagesOfItsKeyThatComeAfterWaitForIt() throws Exception {
        context = newContext();
        registerWithRetry();
        context.start();
        supply("A", "A1");
        Awaitility.await().atMost(10, TimeUnit.SECONDS)
            .until(() -> new FileFlowExecutionDumpRepository(dumps).findByFlow("customer-updates").size() == 1);
        context.stop();

        // the process starts again: the retry is still due in a few seconds, and a message of the same key arrives first
        context = newContext();
        registerWithRetry();
        context.start();
        supply("A", "A2");
        supply("B", "B1");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> updated().contains("B1"));
        Thread.sleep(500);
        Assert.assertFalse("A2 must not overtake the retry of A1: " + trace, updated().contains("A2"));

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> updated().contains("A2"));
        Assert.assertTrue("A1 goes first: " + trace, updated().indexOf("A1") < updated().indexOf("A2"));
    }

    @Test
    public void givenAnExchangeWasWaitingAtABarrierWhenTheProcessStopped_thenTheMessagesOfItsKeyThatComeAfterWaitForIt() throws Exception {
        context = newContext();
        registerWithBarrier();
        context.start();
        supply("A", "A1");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> trace.contains("enter:A1"));
        Thread.sleep(300);
        context.stop();

        context = newContext();
        registerWithBarrier();
        context.start();
        supply("A", "A2");
        Thread.sleep(500);
        Assert.assertFalse("A2 must not enter the flow while A1 waits at the barrier: " + trace, trace.contains("enter:A2"));

        final Exchange payment = context.createExchange("payment-of-A");
        payment.putHeader("customer-id", "A");
        context.supplyExchange("queue://payment", payment);

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> updated().size() == 2);
        Assert.assertEquals(List.of("A1", "A2"), updated());
        Assert.assertTrue("A1 is updated before A2 enters: " + trace, trace.indexOf("update:A1") < trace.indexOf("enter:A2"));
    }

    private void registerWithBarrier() {
        context.registerFlowDefinition(Pipelite.defineFlow("customer-updates")
            .fromSource("queue://customer-updates", (SourceConcurrencyConfigurer c) -> {
                c.durableInbox(false);
                c.concurrency(4).orderedBy(k -> k.headerName("customer-id"));
            })
            .process("enter", (exchange, contribution) -> trace.add("enter:" + exchange.getInputPayloadAs(String.class)))
            .barrier("wait-for-payment", b -> b
                .correlateBy(c -> c.headerName("customer-id"))
                .await("queue://payment")
                .timeout(Duration.ofMinutes(10)))
            .process("update", (exchange, contribution) -> trace.add("update:" + exchange.getInputPayloadAs(String.class)))
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("payment")
            .fromSource("queue://payment")
            .process("idle", (exchange, contribution) -> { })
            .build());
    }
}
