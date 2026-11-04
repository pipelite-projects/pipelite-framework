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
import io.pipelite.core.flow.barrier.BarrierFailedException;
import io.pipelite.core.flow.barrier.BarrierInMemoryRepository;
import io.pipelite.core.flow.barrier.BarrierTimeoutException;
import io.pipelite.core.flow.execution.deadletter.DeadLetteredExchange;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.dsl.Exchange;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Issue #150: an exchange that waits at a barrier longer than the barrier allows goes to the dead letter queue, and
 * what comes after that does not release it nor leave the flows that await its own flow waiting.
 */
public class PipeliteBarrierTimeoutIntegrationTest {

    private DefaultPipeliteContext context;
    private final List<DeadLetteredExchange> deadLetters = new CopyOnWriteArrayList<>();
    private final List<String> shipped = new CopyOnWriteArrayList<>();
    private final List<Throwable> closingFailures = new CopyOnWriteArrayList<>();
    private final List<String> closed = new CopyOnWriteArrayList<>();

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
        context.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());
        context.setBarrierRepository(new BarrierInMemoryRepository());
        context.setDeadLetterQueueRepository(deadLetters::add);
        context.setBarrierScanPeriod(Duration.ofMillis(100));
    }

    @After
    public void tearDown() {
        context.stop();
    }

    private void registerFlows() {
        context.registerFlowDefinition(Pipelite.defineFlow("ship-order")
            .fromSource("queue://a-in")
            .barrier("preparation", b -> b
                .correlateBy(c -> c.headerName("order-id"))
                .await("queue://step-b")
                .await("queue://step-c")
                .timeout(Duration.ofSeconds(1)))
            .process("ship", (exchange, contribution) -> shipped.add(exchange.getInputPayloadAs(String.class)))
            .build());
        // the flow that awaits ship-order, for as long as it takes
        context.registerFlowDefinition(Pipelite.defineFlow("close-order")
            .fromSource("queue://closing-in")
            .barrier("shipped", b -> b
                .correlateBy(c -> c.headerName("order-id"))
                .await("queue://a-in")
                .timeout(Duration.ofMinutes(10)))
            .process("close", (exchange, contribution) -> closed.add(exchange.getInputPayloadAs(String.class)))
            .withExceptionHandler((exception, exchange) -> closingFailures.add(exception))
            .build());
        for (String queue : List.of("step-b", "step-c")) {
            context.registerFlowDefinition(Pipelite.defineFlow(queue)
                .fromSource("queue://" + queue)
                .process("idle", (exchange, contribution) -> { })
                .build());
        }
        context.start();
    }

    private void supply(String queue, String orderId, String payload) {
        final Exchange exchange = context.createExchange(payload);
        exchange.putHeader("order-id", orderId);
        context.supplyExchange("queue://" + queue, exchange);
    }

    @Test
    public void givenAnAwaitedFlowNeverCompletes_thenTheExchangeGoesToTheDeadLetterQueueAfterTheTimeout() throws Exception {
        registerFlows();

        supply("a-in", "o-1", "X");
        supply("step-b", "o-1", "Y");
        Thread.sleep(500);
        Assert.assertTrue("it is still within its time", deadLetters.isEmpty());

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> deadLetters.size() == 1);

        final DeadLetteredExchange entry = deadLetters.get(0);
        Assert.assertEquals("ship-order", entry.getFlowName());
        Assert.assertEquals("preparation", entry.getFailedProcessor());
        Assert.assertEquals(BarrierTimeoutException.class.getName(), entry.getExceptionType());
        Assert.assertTrue(entry.getExceptionMessage(), entry.getExceptionMessage().contains("step-c"));
        Assert.assertTrue(shipped.isEmpty());
    }

    @Test
    public void givenTheExchangeWasGivenUp_thenTheFlowsItAwaitedCompletingLaterReleaseNothing() throws Exception {
        registerFlows();
        supply("a-in", "o-1", "X");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> deadLetters.size() == 1);

        supply("step-b", "o-1", "Y");
        supply("step-c", "o-1", "Z");
        Thread.sleep(600);

        Assert.assertTrue("given up is given up", shipped.isEmpty());
        Assert.assertEquals("and only once", 1, deadLetters.size());
    }

    @Test
    public void givenAnExchangeIsGivenUp_thenTheFlowThatAwaitsItsFlowFailsAtOnce() throws Exception {
        registerFlows();
        supply("closing-in", "o-1", "closing");
        Thread.sleep(300);
        supply("a-in", "o-1", "X");
        Thread.sleep(300);
        Assert.assertTrue("nothing yet: the timeout of the first barrier is not due", closingFailures.isEmpty());

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> closingFailures.size() == 1);

        Assert.assertTrue(closingFailures.get(0) instanceof BarrierFailedException);
        Assert.assertTrue(closed.isEmpty());
        Assert.assertEquals("only the exchange that waited too long is in the dead letter queue", 1, deadLetters.size());
    }
}
