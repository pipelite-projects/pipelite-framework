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
import io.pipelite.dsl.ChannelProtocols;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proves the actual point of issue #68: a {@code .withRetryChannel(...)} dump left pending on
 * disk when a process stops (crash, restart, redeploy) is picked up by a <em>brand new</em>
 * {@code DefaultPipeliteContext} - a different JVM object graph entirely, standing in for a real
 * process restart - as long as the new process resolves {@code pipelite.home} to the same
 * directory and registers a flow with the same name/source/processor. No extra wiring is needed
 * beyond what #68 already built: {@code FileFlowExecutionDumpRepository} is the default, and
 * {@code RetryService}'s own poll loop picks up whatever is already on disk the moment it starts,
 * exactly as it would for a dump saved by the very same process.
 */
public class PipeliteFlowExecutionDumpSurvivesRestartIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String FLOW_NAME = "resume-test-flow";
    private static final String SOURCE = "resume-test-ingress";
    private static final String PROCESSOR_NAME = "process-order";

    @Test
    public void givenAPendingDumpOnDisk_whenANewContextStartsWithTheSameHomeAndFlow_thenItResumesAndRemovesTheDump() throws IOException {

        final String previousHome = System.getProperty("pipelite.home");
        System.setProperty("pipelite.home", temporaryFolder.getRoot().toPath().toString());
        try {

            // --- "Run 1": always fails, leaves a dump pending, then the process "crashes" ---

            final DefaultPipeliteContext firstRun = new DefaultPipeliteContext();
            final AtomicInteger firstRunAttempts = new AtomicInteger(0);

            final FlowDefinition failingFlow = Pipelite.defineFlow(FLOW_NAME)
                .fromSource(ChannelProtocols.queueURL(SOURCE))
                .process(PROCESSOR_NAME, (io, c) -> {
                    firstRunAttempts.incrementAndGet();
                    throw new RuntimeException("simulated persistent failure - this process never recovers");
                })
                .toSink("resume-test-end")
                .withRetry(retry -> retry.maxAttempts(50).onErrorChannel(err -> err.toDLQ()))
                .build();

            firstRun.registerFlowDefinition(failingFlow);
            firstRun.start();

            final ExchangeFactory exchangeFactory = firstRun.getExchangeFactory();
            firstRun.supplyExchange(ChannelProtocols.queueURL(SOURCE), exchangeFactory.createExchange("order-42"));

            final Path dumpsDirectory = temporaryFolder.getRoot().toPath().resolve("state").resolve("retry");
            Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> countDumpFiles(dumpsDirectory) >= 1);

            // Simulates a crash/kill: stop this context without ever letting the message succeed.
            // The pending dump(s) it already wrote are left behind on disk exactly as a real
            // crash would leave them (a graceful stop is a stand-in here - the dump's durability
            // doesn't depend on how the process ends, only on it having been fsync'd already).
            firstRun.stop();

            final long dumpsLeftBehind = countDumpFiles(dumpsDirectory);
            Assert.assertTrue("expected at least one dump left on disk after the simulated crash", dumpsLeftBehind >= 1);

            // --- "Run 2": a brand new context/object graph, same pipelite.home, same flow
            //     identity - but this time the processor succeeds, standing in for whatever fixed
            //     the underlying issue across the restart (a deploy, a config fix, an upstream
            //     dependency recovering) ---

            final DefaultPipeliteContext secondRun = new DefaultPipeliteContext();
            final AtomicInteger secondRunSuccesses = new AtomicInteger(0);

            // .withRetry(...) kept, matching realistic usage: a flow's DSL definition is
            // re-declared identically on every process start, restart included - only the
            // processor's own behavior differs here, standing in for whatever was actually fixed.
            // This also matters mechanically: the retry-channel (and the poll loop that would
            // ever pick this dump back up) is only created at all if some registered flow
            // declares .withRetry(...) - confirmed by first writing this test without it
            // here and watching the second run hang forever with no RetryService in its logs.
            final FlowDefinition recoveredFlow = Pipelite.defineFlow(FLOW_NAME)
                .fromSource(ChannelProtocols.queueURL(SOURCE))
                .process(PROCESSOR_NAME, (io, c) -> secondRunSuccesses.incrementAndGet())
                .toSink("resume-test-end")
                .withRetry(retry -> retry.maxAttempts(50).onErrorChannel(err -> err.toDLQ()))
                .build();

            secondRun.registerFlowDefinition(recoveredFlow);
            secondRun.start();

            try {
                // No new supplyExchange(...) call here on purpose - the only exchange resolved
                // below is the one left pending on disk from "run 1".
                Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> secondRunSuccesses.get() >= 1);
                Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> countDumpFiles(dumpsDirectory) == 0);
            } finally {
                secondRun.stop();
            }

        } finally {
            if (previousHome != null) {
                System.setProperty("pipelite.home", previousHome);
            } else {
                System.clearProperty("pipelite.home");
            }
        }
    }

    private static long countDumpFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".dump")).count();
        }
    }

}
