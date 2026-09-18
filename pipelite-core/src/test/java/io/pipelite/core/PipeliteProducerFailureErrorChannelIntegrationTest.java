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
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Regression coverage for issue #89: a producer's (a flow's {@code .toSink(...)}) own failure
 * previously bypassed every configured {@code ExceptionHandler} entirely - no retry, no dead
 * letter - because no concrete {@code Producer} ever consulted {@code exceptionHandler}. Fixed by
 * making {@code DefaultProducer#process(Exchange)} final, wrapping the same try/catch shape
 * {@code AbstractProcessorNode} already had around a new {@code doProcess(Exchange)} hook every
 * concrete producer now implements instead.
 */
public class PipeliteProducerFailureErrorChannelIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private PipeliteContext pipeliteContext;

    @Before
    public void setup() {
        pipeliteContext = Pipelite.createContext();
    }

    @Test
    public void givenAProducerFails_whenAnErrorChannelIsConfigured_thenTheExchangeIsRoutedToTheDeadLetterFlow() throws IOException {

        // The sink's parent path is an existing regular file, not a directory - FileProducer's own
        // Files.createDirectories(parent) call fails with FileAlreadyExistsException, which
        // FileProducer wraps into an IllegalStateException and (before the #89 fix) always
        // propagated uncaught, regardless of any configured error channel.
        final Path blockingFile = temporaryFolder.newFile("not-a-directory").toPath();
        final Path unwritableTarget = blockingFile.resolve("out.txt");

        final AtomicBoolean deadLettered = new AtomicBoolean(false);

        final FlowDefinition deadLetterFlow = Pipelite.defineFlow("producer-failure-dead-letter-flow")
            .fromSource("producer-failure-dead-letter")
            .process("mark-dead-lettered", (io, c) -> deadLettered.set(true))
            .build();

        final FlowDefinition mainFlow = Pipelite.defineFlow("producer-failure-flow")
            .fromSource("producer-failure-in")
            .toSink("file://" + unwritableTarget)
            .withErrorChannel(err -> err.toChannel("producer-failure-dead-letter-flow"))
            .build();

        pipeliteContext.registerFlowDefinition(deadLetterFlow);
        pipeliteContext.registerFlowDefinition(mainFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("producer-failure-in", exchangeFactory.createExchange("payload"));

        // Before the #89 fix, this would time out: the producer's IllegalStateException
        // propagated straight past the configured error channel with no dead-letter routing at all.
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(deadLettered::get);
    }

}
