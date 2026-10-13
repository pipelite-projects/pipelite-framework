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
import io.pipelite.dsl.definition.FlowDefinition;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Issue #150: an order that is waiting at a barrier when the process stops is still waiting when it starts
 * again, because the arrival that was acknowledged to its inbox is on Pipelite Home.
 */
public class PipeliteBarrierRestartIntegrationTest {

    private record StepResult(String orderId, String step) implements Serializable {
    }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static FlowDefinition preparation(List<List<String>> released) {
        return Pipelite.defineFlow("preparation")
            .fromSource("queue://step-results")
            .barrier("all-steps", b -> b
                .correlateBy(c -> c.payloadAttribute(StepResult::orderId))
                .expect("record", "check")
                .contributorBy(ex -> ex.getInputPayloadAs(StepResult.class).step())
                .timeout(Duration.ofMinutes(10)))
            .process("next-step", (exchange, contribution) -> released.add(
                exchange.getInputPayloadAs(List.class).stream().map(r -> ((StepResult) r).step()).map(String::valueOf).toList()))
            .build();
    }

    @Test
    public void givenOneContributorArrivedBeforeTheRestart_thenTheOtherCompletesTheBarrierAfterIt() throws IOException {

        final String previousHome = System.getProperty("pipelite.home");
        System.setProperty("pipelite.home", temporaryFolder.getRoot().toPath().toString());
        try {
            // --- Run 1: "record" arrives, the barrier waits, the process stops ---
            final List<List<String>> releasedByFirstRun = new CopyOnWriteArrayList<>();
            final PipeliteContext firstRun = new DefaultPipeliteContext();
            firstRun.registerFlowDefinition(preparation(releasedByFirstRun));
            firstRun.start();
            firstRun.supplyExchange("queue://step-results", firstRun.createExchange(new StepResult("order-42", "record")));

            final Path barriers = temporaryFolder.getRoot().toPath().resolve("state").resolve("barrier");
            Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> countBarrierFiles(barriers) == 1);
            firstRun.stop();
            Assert.assertTrue("the barrier is still waiting", releasedByFirstRun.isEmpty());

            // --- Run 2: a new context on the same home, "check" arrives ---
            final List<List<String>> releasedBySecondRun = new CopyOnWriteArrayList<>();
            final PipeliteContext secondRun = new DefaultPipeliteContext();
            try {
                secondRun.registerFlowDefinition(preparation(releasedBySecondRun));
                secondRun.start();
                secondRun.supplyExchange("queue://step-results", secondRun.createExchange(new StepResult("order-42", "check")));

                Awaitility.await().atMost(15, TimeUnit.SECONDS).until(() -> !releasedBySecondRun.isEmpty());
                Assert.assertEquals(1, releasedBySecondRun.size());
                Assert.assertEquals(List.of("record", "check"), releasedBySecondRun.get(0));
            } finally {
                secondRun.stop();
            }
        } finally {
            if (previousHome == null) {
                System.clearProperty("pipelite.home");
            } else {
                System.setProperty("pipelite.home", previousHome);
            }
        }
    }

    private static long countBarrierFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".barrier")).count();
        }
    }
}
