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
import io.pipelite.core.definition.DuplicateProcessorNameException;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Covers issue #5 ("[Core] Dead Letter channel (DLC)"): {@code .withErrorChannel(err ->
 * err.toChannel(target))} usable independently of retry (immediate dead-lettering, no retry) and
 * composed with it via {@code .withRetry(retry -> retry.onErrorChannel(...))} (retry first,
 * dead-letter only once exhausted). {@code toChannel(...)} was renamed and broadened from
 * {@code definedFlow(...)} by issue #91 to also accept protocol-qualified channel adapter URLs, and
 * by issue #102 to accept <em>only</em> URLs: an internal flow is addressed as {@code
 * queue://<queue name>}, never by its flow name.
 */
public class PipeliteDeadLetterChannelIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String previousHome;
    private ConfigurablePipeliteContext pipeliteContext;

    @Before
    public void setup() throws Exception {
        // Isolated pipelite.home: the default durable inbox is file-backed, and would otherwise
        // accumulate state under the real ~/.pipelite.
        previousHome = System.getProperty("pipelite.home");
        System.setProperty("pipelite.home", temporaryFolder.newFolder("home").toPath().toString());
        pipeliteContext = (ConfigurablePipeliteContext) Pipelite.createContext();
        // Retry-routing/dead-letter mechanics only, not persistence - in-memory keeps this
        // hermetic (DefaultPipeliteContext's own default is now file-backed under PipeliteHome,
        // see issue #68).
        pipeliteContext.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());
    }

    @After
    public void tearDown() {
        pipeliteContext.stop();
        if (previousHome != null) {
            System.setProperty("pipelite.home", previousHome);
        } else {
            System.clearProperty("pipelite.home");
        }
    }

    @Test
    public void givenDeadLetterChannelAlone_whenProcessorThrows_thenRoutedImmediatelyWithNoRetry(){

        final AtomicInteger attemptCount = new AtomicInteger(0);
        final AtomicReference<ExchangeImpl> deadLettered = new AtomicReference<>();

        final FlowDefinition deadLetterQueue = Pipelite.defineFlow("dlc-only-poison-queue")
            .fromSource("queue://dlc-only-poison-queue")
            .process("capture", (io, c) -> deadLettered.set((ExchangeImpl) io))
            .toSink("dlc-only-poison-out")
            .build();

        final FlowDefinition mainFlow = Pipelite.defineFlow("dlc-only-main-flow")
            .fromSource("queue://dlc-only-in")
            .process("always-fail", (io, c) -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("simulated poison message");
            })
            .toSink("dlc-only-out")
            .withErrorChannel(err -> err.toChannel("queue://dlc-only-poison-queue"))
            .build();

        pipeliteContext.registerFlowDefinition(deadLetterQueue);
        pipeliteContext.registerFlowDefinition(mainFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("queue://dlc-only-in", exchangeFactory.createExchange("poison-payload"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> deadLettered.get() != null);

        // No retry configured: the very first failure must have gone straight to the DLC.
        assertEquals(1, attemptCount.get());
        assertEquals("poison-payload", deadLettered.get().getInputPayloadAs(String.class));
        assertTrue(deadLettered.get().hasHeader(IOKeys.FAILURE_EXCEPTION_TYPE_HEADER_NAME));
        assertTrue(deadLettered.get().tryGetHeader(IOKeys.FAILURE_STACK_TRACE_HEADER_NAME).isPresent());
    }

    @Test
    public void givenRetryChannelAndDeadLetterChannel_whenAttemptsAreExhausted_thenRoutedToDeadLetterOnlyAfterExhaustion(){

        final AtomicInteger attemptCount = new AtomicInteger(0);
        final AtomicReference<ExchangeImpl> deadLettered = new AtomicReference<>();

        final FlowDefinition deadLetterQueue = Pipelite.defineFlow("dlc-composed-poison-queue")
            .fromSource("queue://dlc-composed-poison-queue")
            .process("capture", (io, c) -> deadLettered.set((ExchangeImpl) io))
            .toSink("dlc-composed-poison-out")
            .build();

        final FlowDefinition mainFlow = Pipelite.defineFlow("dlc-composed-main-flow")
            .fromSource("queue://dlc-composed-in")
            .process("always-fail", (io, c) -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("simulated persistent failure");
            })
            .toSink("dlc-composed-out")
            .withRetry(retry -> retry
                .maxAttempts(3)
                .onErrorChannel(err -> err.toChannel("queue://dlc-composed-poison-queue")))
            .build();

        pipeliteContext.registerFlowDefinition(deadLetterQueue);
        pipeliteContext.registerFlowDefinition(mainFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("queue://dlc-composed-in", exchangeFactory.createExchange("persistent-poison-payload"));

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> deadLettered.get() != null);

        // Exactly maxAttempts real attempts, not one (proves retry actually ran first) and not
        // unbounded (proves the configured cap, not the old always-retry bug, stopped it).
        assertEquals(3, attemptCount.get());
        assertEquals("persistent-poison-payload", deadLettered.get().getInputPayloadAs(String.class));
    }

    @Test
    public void givenDuplicateProcessorName_whenFlowIsDefined_thenThrowsDuplicateProcessorNameException(){
        try {
            Pipelite.defineFlow("duplicate-name-flow")
                .fromSource("queue://duplicate-name-in")
                .process("same-name", (io, c) -> {})
                .process("same-name", (io, c) -> {})
                .toSink("duplicate-name-out")
                .build();
            fail("Expected DuplicateProcessorNameException");
        } catch (DuplicateProcessorNameException expected) {
            assertTrue(expected.getMessage().contains("same-name"));
        }
    }

    /**
     * Issue #91 broadened {@code toChannel(...)} to also accept a protocol-qualified channel
     * adapter URL, delivered directly with no {@code Flow} defined by its own name required - the
     * receiving side here is a plain flow whose {@code fromSource("queue://...")} matches the
     * target resource, exactly like any other channel-adapter delivery, not a
     * {@code tryFindFlowByName(...)} lookup.
     */
    @Test
    public void givenProtocolQualifiedTarget_whenProcessorThrows_thenDeliveredDirectlyToTheChannelAdapter(){

        final AtomicReference<ExchangeImpl> deadLettered = new AtomicReference<>();

        // A queue source is what QueueChannelAdapter registers a consumer for (see
        // QueueChannelAdapter#onFlowRegistered); the same queue:// URL is what the sending side
        // (below) delivers to.
        final FlowDefinition deadLetterReceiver = Pipelite.defineFlow("link-qualified-dlc-receiver")
            .fromSource("queue://link-qualified-dlc-poison-queue")
            .process("capture", (io, c) -> deadLettered.set((ExchangeImpl) io))
            .toSink("link-qualified-dlc-poison-out")
            .build();

        final FlowDefinition mainFlow = Pipelite.defineFlow("link-qualified-dlc-flow")
            .fromSource("queue://link-qualified-dlc-in")
            .process("always-fail", (io, c) -> { throw new RuntimeException("boom"); })
            .toSink("link-qualified-dlc-out")
            .withErrorChannel(err -> err.toChannel("queue://link-qualified-dlc-poison-queue"))
            .build();

        pipeliteContext.registerFlowDefinition(deadLetterReceiver);
        pipeliteContext.registerFlowDefinition(mainFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("queue://link-qualified-dlc-in", exchangeFactory.createExchange("poison-payload"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> deadLettered.get() != null);
        assertEquals("poison-payload", deadLettered.get().getInputPayloadAs(String.class));
    }

    /**
     * Issue #102: a flow is addressed by URL, never by name. A bare value is rejected when the
     * flow is defined, with a message saying what to write, instead of being read as a flow name
     * (before) or failing at the first dead-lettered message.
     */
    @Test
    public void givenABareName_whenToChannelIsCalled_thenRejectedAtDefinitionTimeSayingWhatToWrite(){
        try {
            Pipelite.defineFlow("bare-name-dlc-main-flow")
                .fromSource("queue://bare-name-dlc-in")
                .process("always-fail", (io, c) -> { throw new RuntimeException("boom"); })
                .toSink("bare-name-dlc-out")
                .withErrorChannel(err -> err.toChannel("bare-name-dlc-poison-queue"))
                .build();
            fail("Expected a bare name to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("toChannel(...)"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("queue://bare-name-dlc-poison-queue"));
        }
    }

    @Test
    public void givenFlowNameDiffersFromItsSourceEndpointName_whenToChannelIsCalled_thenTheSourceEndpointNameAddressesIt(){

        final AtomicReference<ExchangeImpl> deadLettered = new AtomicReference<>();

        // The dead-letter flow's own Pipelite.defineFlow(...) name is deliberately different from
        // the queue its fromSource(...) reads: only the latter is an address, the flow name is an identity.
        final FlowDefinition deadLetterQueue = Pipelite.defineFlow("distinct-flow-name-dlc-queue")
            .fromSource("queue://totally-unrelated-source-resource")
            .process("capture", (io, c) -> deadLettered.set((ExchangeImpl) io))
            .toSink("distinct-flow-name-dlc-out")
            .build();

        final FlowDefinition mainFlow = Pipelite.defineFlow("distinct-flow-name-dlc-main-flow")
            .fromSource("queue://distinct-flow-name-dlc-in")
            .process("always-fail", (io, c) -> { throw new RuntimeException("boom"); })
            .toSink("distinct-flow-name-dlc-out-2")
            .withErrorChannel(err -> err.toChannel("queue://totally-unrelated-source-resource"))
            .build();

        pipeliteContext.registerFlowDefinition(deadLetterQueue);
        pipeliteContext.registerFlowDefinition(mainFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("queue://distinct-flow-name-dlc-in", exchangeFactory.createExchange("poison-payload"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> deadLettered.get() != null);
        assertEquals("poison-payload", deadLettered.get().getInputPayloadAs(String.class));
    }

    /**
     * Issue #99: the retry path used to look the target up as a flow named after the URL, so a
     * protocol-qualified target worked without retry and lost the exchange once retry attempts
     * were exhausted. Both paths deliver through the same {@code supplyExchange} now.
     */
    @Test
    public void givenRetryAndAQueueTarget_whenAttemptsAreExhausted_thenDeliveredToTheLinkedFlow(){

        final AtomicInteger attemptCount = new AtomicInteger(0);
        final AtomicReference<ExchangeImpl> deadLettered = new AtomicReference<>();

        final FlowDefinition receiver = Pipelite.defineFlow("retry-link-dlc-receiver")
            .fromSource("queue://retry-link-dlc-poison-queue")
            .process("capture", (io, c) -> deadLettered.set((ExchangeImpl) io))
            .build();

        final FlowDefinition mainFlow = Pipelite.defineFlow("retry-link-dlc-main-flow")
            .fromSource("queue://retry-link-dlc-in")
            .process("always-fail", (io, c) -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("simulated persistent failure");
            })
            .toSink("retry-link-dlc-out")
            .withRetry(retry -> retry
                .maxAttempts(2)
                .onErrorChannel(err -> err.toChannel("queue://retry-link-dlc-poison-queue")))
            .build();

        pipeliteContext.registerFlowDefinition(receiver);
        pipeliteContext.registerFlowDefinition(mainFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("queue://retry-link-dlc-in", exchangeFactory.createExchange("persistent-poison-payload"));

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> deadLettered.get() != null);

        assertEquals(2, attemptCount.get());
        assertEquals("persistent-poison-payload", deadLettered.get().getInputPayloadAs(String.class));
    }

}
