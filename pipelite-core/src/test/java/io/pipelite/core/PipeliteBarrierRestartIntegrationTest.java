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
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.flow.barrier.BarrierFileRepository;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.definition.FlowDefinition;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Issue #150: an exchange that waits at a barrier when the process stops is still waiting when it starts again,
 * and one whose wait was over but that had not been resumed is resumed by the recovery, once.
 */
public class PipeliteBarrierRestartIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static FlowDefinition shipOrder(List<String> resumed, List<Throwable> failures) {
        return Pipelite.defineFlow("ship-order")
            .fromSource("queue://a-in")
            .barrier("preparation", b -> b
                .correlateBy(c -> c.headerName("order-id"))
                .await("queue://step-b")
                .await("queue://step-c")
                .timeout(Duration.ofMinutes(10)))
            .process("next", (exchange, contribution) -> resumed.add(exchange.getInputPayloadAs(String.class)))
            .withExceptionHandler((exception, exchange) -> failures.add(exception))
            .build();
    }

    /** The flows the barrier awaits: they only have to exist, the tests tell the coordinator what they would. */
    private static void registerAwaitedFlows(PipeliteContext context) {
        for (String queue : List.of("step-b", "step-c")) {
            context.registerFlowDefinition(Pipelite.defineFlow(queue)
                .fromSource("queue://" + queue)
                .process("idle", (exchange, contribution) -> { })
                .build());
        }
    }

    private Path barrierDirectory() {
        return temporaryFolder.getRoot().toPath().resolve("state").resolve("barrier");
    }

    private long files(String subdirectory) throws IOException {
        final Path path = barrierDirectory().resolve(subdirectory);
        if (!Files.isDirectory(path)) {
            return 0;
        }
        try (Stream<Path> list = Files.list(path)) {
            return list.filter(file -> !file.getFileName().toString().endsWith(".lock")).count();
        }
    }

    private static void supply(PipeliteContext context, String orderId, String payload) {
        final Exchange exchange = context.createExchange(payload);
        exchange.putHeader("order-id", orderId);
        context.supplyExchange("queue://a-in", exchange);
    }

    private void runWithTheTemporaryHome(ThrowingRunnable test) throws Exception {
        final String previousHome = System.getProperty("pipelite.home");
        System.setProperty("pipelite.home", temporaryFolder.getRoot().toPath().toString());
        try {
            test.run();
        } finally {
            if (previousHome == null) {
                System.clearProperty("pipelite.home");
            } else {
                System.setProperty("pipelite.home", previousHome);
            }
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Test
    public void givenAnExchangeWaitedBeforeTheRestart_thenTheFlowsThatCompleteAfterItReleaseIt() throws Exception {
        runWithTheTemporaryHome(() -> {
            final List<String> resumedByFirstRun = new CopyOnWriteArrayList<>();
            final PipeliteContext firstRun = new DefaultPipeliteContext();
            firstRun.registerFlowDefinition(shipOrder(resumedByFirstRun, new CopyOnWriteArrayList<>()));
            registerAwaitedFlows(firstRun);
            firstRun.start();
            supply(firstRun, "o-1", "payload-1");
            Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> files("waiters") == 1);
            ((InternalPipeliteContext) firstRun).getBarrierCoordinator().flowCompleted("step-b", "o-1");
            firstRun.stop();
            Assert.assertTrue("the barrier was still waiting", resumedByFirstRun.isEmpty());

            final List<String> resumedBySecondRun = new CopyOnWriteArrayList<>();
            final PipeliteContext secondRun = new DefaultPipeliteContext();
            try {
                secondRun.registerFlowDefinition(shipOrder(resumedBySecondRun, new CopyOnWriteArrayList<>()));
                registerAwaitedFlows(secondRun);
                secondRun.start();
                final var coordinator = ((InternalPipeliteContext) secondRun).getBarrierCoordinator();
                coordinator.flowCompleted("step-c", "o-1");

                Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> !resumedBySecondRun.isEmpty());
                Assert.assertEquals(List.of("payload-1"), resumedBySecondRun);
                Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> files("waiters") == 0);
            } finally {
                secondRun.stop();
            }
        });
    }

    @Test
    public void givenAWaitThatWasReleasedButNotResumedWhenTheProcessStopped_thenTheRecoveryResumesItOnceAndForgetsIt() throws Exception {
        runWithTheTemporaryHome(() -> {
            final PipeliteContext firstRun = new DefaultPipeliteContext();
            firstRun.registerFlowDefinition(shipOrder(new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>()));
            registerAwaitedFlows(firstRun);
            firstRun.start();
            supply(firstRun, "o-1", "payload-1");
            Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> files("waiters") == 1);
            firstRun.stop();

            // what a crash after the release and before the resume leaves on disk
            final BarrierFileRepository offline = new BarrierFileRepository(barrierDirectory());
            final Instant retainUntil = Instant.now().plusSeconds(600);
            offline.complete("step-b", "o-1", retainUntil);
            Assert.assertEquals(1, offline.complete("step-c", "o-1", retainUntil).size());

            final List<String> resumed = new CopyOnWriteArrayList<>();
            final PipeliteContext secondRun = new DefaultPipeliteContext();
            try {
                secondRun.registerFlowDefinition(shipOrder(resumed, new CopyOnWriteArrayList<>()));
                registerAwaitedFlows(secondRun);
                secondRun.start();

                Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> !resumed.isEmpty());
                Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> files("waiters") == 0);
                Thread.sleep(500);
                Assert.assertEquals("resumed once", List.of("payload-1"), resumed);
            } finally {
                secondRun.stop();
            }
        });
    }

    @Test
    public void givenAWaitWhoseFlowFailedWhenTheProcessStopped_thenTheRecoveryHandsItToTheErrorHandling() throws Exception {
        runWithTheTemporaryHome(() -> {
            final PipeliteContext firstRun = new DefaultPipeliteContext();
            firstRun.registerFlowDefinition(shipOrder(new CopyOnWriteArrayList<>(), new CopyOnWriteArrayList<>()));
            registerAwaitedFlows(firstRun);
            firstRun.start();
            supply(firstRun, "o-1", "payload-1");
            Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> files("waiters") == 1);
            firstRun.stop();

            final BarrierFileRepository offline = new BarrierFileRepository(barrierDirectory());
            Assert.assertEquals(1, offline.fail("step-b", "o-1", Instant.now().plusSeconds(600)).size());

            final List<String> resumed = new CopyOnWriteArrayList<>();
            final List<Throwable> failures = new CopyOnWriteArrayList<>();
            final PipeliteContext secondRun = new DefaultPipeliteContext();
            try {
                secondRun.registerFlowDefinition(shipOrder(resumed, failures));
                registerAwaitedFlows(secondRun);
                secondRun.start();

                Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> failures.size() == 1);
                Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> files("waiters") == 0);
                Assert.assertTrue(resumed.isEmpty());
            } finally {
                secondRun.stop();
            }
        });
    }
}
