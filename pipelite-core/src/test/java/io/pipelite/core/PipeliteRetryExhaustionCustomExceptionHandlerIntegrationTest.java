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
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Regression coverage for issue #91's {@code .withRetry(...).onExceptionHandler(...)} exhaustion
 * action: the custom handler is never serialized into the {@code FlowExecutionDump} (it's often a
 * lambda, not required to be {@code Serializable}) - instead {@code RetryStrategyFilter}
 * re-resolves it fresh from the currently-registered flow by name at exhaustion time. Mirrors
 * {@code PipeliteFlowExecutionDumpSurvivesRestartIntegrationTest}'s two-{@code
 * DefaultPipeliteContext} pattern to prove that re-resolution survives a process restart, not just
 * an in-process retry.
 */
public class PipeliteRetryExhaustionCustomExceptionHandlerIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void givenRetryExhaustedWithCustomExceptionHandler_thenTheHandlerIsInvokedInsteadOfRoutingAnywhere() throws Exception {

        // Isolated pipelite.home, same reasoning as the restart test below: this context uses the
        // real, default (file-backed) FlowExecutionDumpRepository/DurableInboxProvider, so without
        // isolation, state left behind by a previous run of this same test (or any other test
        // using the same resource names against the real home) would interfere.
        final String previousHome = System.getProperty("pipelite.home");
        System.setProperty("pipelite.home", temporaryFolder.newFolder("isolated-home").toPath().toString());
        try {

            final AtomicInteger attemptCount = new AtomicInteger(0);
            final AtomicReference<Throwable> capturedException = new AtomicReference<>();
            final AtomicReference<Object> capturedPayload = new AtomicReference<>();

            final DefaultPipeliteContext pipeliteContext = new DefaultPipeliteContext();

            final FlowDefinition testFlow = Pipelite.defineFlow("retry-exhaustion-custom-handler-flow")
                .fromSource("retry-exhaustion-custom-handler-in")
                .process("always-fail", (io, c) -> {
                    attemptCount.incrementAndGet();
                    throw new RuntimeException("simulated persistent failure");
                })
                .toSink("retry-exhaustion-custom-handler-out")
                .withRetry(retry -> retry
                    .maxAttempts(2)
                    .onExceptionHandler((exception, ioContext) -> {
                        capturedException.set(exception);
                        capturedPayload.set(ioContext.getInputPayloadAs(String.class));
                    }))
                .build();

            pipeliteContext.registerFlowDefinition(testFlow);
            pipeliteContext.start();

            try {
                final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
                pipeliteContext.supplyExchange("link://retry-exhaustion-custom-handler-in", exchangeFactory.createExchange("poison-payload"));

                Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> attemptCount.get() == 2);
                Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> capturedException.get() != null);

                Assert.assertEquals("poison-payload", capturedPayload.get());
            } finally {
                pipeliteContext.stop();
            }
        } finally {
            if (previousHome != null) {
                System.setProperty("pipelite.home", previousHome);
            } else {
                System.clearProperty("pipelite.home");
            }
        }
    }

    @Test
    public void givenRetryExhaustedWithCustomExceptionHandler_whenProcessRestarts_thenTheHandlerIsStillReResolvedAndInvoked() throws Exception {

        final String previousHome = System.getProperty("pipelite.home");
        System.setProperty("pipelite.home", temporaryFolder.getRoot().toPath().toString());
        try {

            // --- "Run 1": always fails, leaves a dump pending, then the process "crashes" ---

            final DefaultPipeliteContext firstRun = new DefaultPipeliteContext();
            final AtomicInteger firstRunAttempts = new AtomicInteger(0);

            final FlowDefinition failingFlow = Pipelite.defineFlow("retry-exhaustion-restart-flow")
                .fromSource("retry-exhaustion-restart-in")
                .process("always-fail", (io, c) -> {
                    firstRunAttempts.incrementAndGet();
                    throw new RuntimeException("simulated persistent failure - never recovers this run");
                })
                .toSink("retry-exhaustion-restart-out")
                .withRetry(retry -> retry
                    .maxAttempts(2)
                    .onExceptionHandler((exception, ioContext) -> {
                        // Never expected to run in this process - attempts never reach maxAttempts
                        // before the simulated crash below.
                        throw new AssertionError("should not be invoked in the first run");
                    }))
                .build();

            firstRun.registerFlowDefinition(failingFlow);
            firstRun.start();

            final ExchangeFactory exchangeFactory = firstRun.getExchangeFactory();
            firstRun.supplyExchange("link://retry-exhaustion-restart-in", exchangeFactory.createExchange("poison-payload"));

            final Path dumpsDirectory = temporaryFolder.getRoot().toPath().resolve("state").resolve("retry");
            Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> firstRunAttempts.get() == 1);
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> countDumpFiles(dumpsDirectory) >= 1);

            // Simulates a crash/kill before the second attempt ever resumes.
            firstRun.stop();

            // --- "Run 2": a brand new context/object graph, same pipelite.home, same flow
            //     identity re-declared with a fresh custom handler instance - proves the handler
            //     is re-resolved from the currently-registered flow, not carried over from run 1
            //     (which would be impossible - the dump never serializes it). ---

            final DefaultPipeliteContext secondRun = new DefaultPipeliteContext();
            final AtomicReference<Throwable> capturedException = new AtomicReference<>();

            final FlowDefinition recoveredFlow = Pipelite.defineFlow("retry-exhaustion-restart-flow")
                .fromSource("retry-exhaustion-restart-in")
                .process("always-fail", (io, c) -> {
                    throw new RuntimeException("still failing after the restart");
                })
                .toSink("retry-exhaustion-restart-out")
                .withRetry(retry -> retry
                    .maxAttempts(2)
                    .onExceptionHandler((exception, ioContext) -> capturedException.set(exception)))
                .build();

            secondRun.registerFlowDefinition(recoveredFlow);
            secondRun.start();

            try {
                // No new supplyExchange(...) here - the only exchange resolved is the one left
                // pending on disk from "run 1", resumed directly at the retry poll loop.
                Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> capturedException.get() != null);
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

    private static long countDumpFiles(Path directory) {
        final java.io.File dir = directory.toFile();
        final java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".dump"));
        return files == null ? 0 : files.length;
    }

}
