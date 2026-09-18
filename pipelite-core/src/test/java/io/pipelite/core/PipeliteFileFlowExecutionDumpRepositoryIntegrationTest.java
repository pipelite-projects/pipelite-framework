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
import io.pipelite.core.flow.execution.dump.FileFlowExecutionDumpRepository;
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
 * Exercises {@link DefaultPipeliteContext#setFlowExecutionDumpRepository(io.pipelite.core.flow.execution.FlowExecutionDumpRepository)}
 * end-to-end through a real retry-channel flow — the regression scenario for the staleness bug
 * fixed alongside {@link FileFlowExecutionDumpRepository} (issue #68): before that fix, {@code
 * RetryChannelDefinitionFactory} was built once in {@code DefaultPipeliteContext}'s constructor,
 * capturing whichever repository existed at that point - a repository swapped in afterward via
 * the setter (but still before {@code start()}) would silently be used only by {@code
 * RetryChannelExceptionHandler}/{@code RetryService}, while the retry-channel's own resolve step
 * kept using the original, stale in-memory repository. That mismatch would make this test hang
 * until the Awaitility timeout, since the dump would never be found (nor ever removed).
 */
public class PipeliteFileFlowExecutionDumpRepositoryIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void givenFileRepositorySetBeforeStart_whenExceptionIsThrown_thenDumpIsWrittenAndLaterRemovedOnRetryResolution() throws IOException {

        final Path dumpsDirectory = temporaryFolder.getRoot().toPath().resolve("retry");

        final DefaultPipeliteContext pipeliteContext = new DefaultPipeliteContext();
        pipeliteContext.setFlowExecutionDumpRepository(new FileFlowExecutionDumpRepository(dumpsDirectory));

        final AtomicInteger counter = new AtomicInteger(0);
        final FlowDefinition testFlow = Pipelite.defineFlow("test-flow")
            .fromSource("ingress")
            .process("throw-once", ((ioContext, contribution) -> {
                if (counter.incrementAndGet() == 1) {
                    throw new RuntimeException("Programmatic exception");
                }
            }))
            .toSink("end")
            .withRetry(retry -> retry.maxAttempts(5).onErrorChannel(err -> err.toDLQ()))
            .build();

        pipeliteContext.registerFlowDefinition(testFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("ingress", exchangeFactory.createExchange("test-message"));

        // Proves RetryChannelExceptionHandler.save(...) landed in the repository passed to the
        // setter, not a stale in-memory default - a dump file must appear on disk.
        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> countDumpFiles(dumpsDirectory) == 1);

        // Proves the retry-channel's own resolve step (RetryPollingConsumer/
        // ResolveExecutionDumpProcessor, wired via RetryChannelDefinitionFactory) polled and
        // removed from the SAME repository instance, not a different, stale one: the flow must
        // reach its sink (counter advances past 1) and the dump file must be gone.
        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> counter.get() > 1 && countDumpFiles(dumpsDirectory) == 0);

        Assert.assertEquals(2, counter.get());
    }

    @Test
    public void givenNoExplicitRepositoryConfigured_whenExceptionIsThrown_thenTheDefaultWritesUnderPipeliteHome() throws IOException {

        final Path customHome = temporaryFolder.getRoot().toPath().resolve("custom-pipelite-home");
        final String previousHome = System.getProperty("pipelite.home");
        System.setProperty("pipelite.home", customHome.toString());
        try {

            // No setFlowExecutionDumpRepository(...) call here - exercising
            // DefaultPipeliteContext's own default (file-backed under PipeliteHome as of #68),
            // not an explicit override like the test above.
            final DefaultPipeliteContext pipeliteContext = new DefaultPipeliteContext();

            final AtomicInteger counter = new AtomicInteger(0);
            final FlowDefinition testFlow = Pipelite.defineFlow("default-repository-test-flow")
                .fromSource("default-repository-ingress")
                .process("throw-once", ((ioContext, contribution) -> {
                    if (counter.incrementAndGet() == 1) {
                        throw new RuntimeException("Programmatic exception");
                    }
                }))
                .toSink("default-repository-end")
                .withRetry(retry -> retry.maxAttempts(5).onErrorChannel(err -> err.toDLQ()))
                .build();

            pipeliteContext.registerFlowDefinition(testFlow);
            pipeliteContext.start();

            final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
            pipeliteContext.supplyExchange("default-repository-ingress", exchangeFactory.createExchange("test-message"));

            final Path expectedDumpsDirectory = customHome.resolve("state").resolve("retry");
            Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> countDumpFiles(expectedDumpsDirectory) == 1);
            Awaitility.await().atMost(30, TimeUnit.SECONDS)
                .until(() -> counter.get() > 1 && countDumpFiles(expectedDumpsDirectory) == 0);

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
