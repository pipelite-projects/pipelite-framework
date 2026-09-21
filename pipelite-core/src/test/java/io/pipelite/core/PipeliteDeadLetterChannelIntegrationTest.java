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
import org.junit.Before;
import org.junit.Test;

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
 * {@code definedFlow(...)} by issue #91 to also accept protocol-qualified channel adapter URLs.
 */
public class PipeliteDeadLetterChannelIntegrationTest {

    private ConfigurablePipeliteContext pipeliteContext;

    @Before
    public void setup(){
        pipeliteContext = (ConfigurablePipeliteContext) Pipelite.createContext();
        // Retry-routing/dead-letter mechanics only, not persistence - in-memory keeps this
        // hermetic (DefaultPipeliteContext's own default is now file-backed under PipeliteHome,
        // see issue #68).
        pipeliteContext.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());
    }

    @Test
    public void givenDeadLetterChannelAlone_whenProcessorThrows_thenRoutedImmediatelyWithNoRetry(){

        final AtomicInteger attemptCount = new AtomicInteger(0);
        final AtomicReference<ExchangeImpl> deadLettered = new AtomicReference<>();

        final FlowDefinition deadLetterQueue = Pipelite.defineFlow("dlc-only-poison-queue")
            .fromSource("dlc-only-poison-queue")
            .process("capture", (io, c) -> deadLettered.set((ExchangeImpl) io))
            .toSink("dlc-only-poison-out")
            .build();

        final FlowDefinition mainFlow = Pipelite.defineFlow("dlc-only-main-flow")
            .fromSource("dlc-only-in")
            .process("always-fail", (io, c) -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("simulated poison message");
            })
            .toSink("dlc-only-out")
            .withErrorChannel(err -> err.toChannel("dlc-only-poison-queue"))
            .build();

        pipeliteContext.registerFlowDefinition(deadLetterQueue);
        pipeliteContext.registerFlowDefinition(mainFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("dlc-only-in", exchangeFactory.createExchange("poison-payload"));

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
            .fromSource("dlc-composed-poison-queue")
            .process("capture", (io, c) -> deadLettered.set((ExchangeImpl) io))
            .toSink("dlc-composed-poison-out")
            .build();

        final FlowDefinition mainFlow = Pipelite.defineFlow("dlc-composed-main-flow")
            .fromSource("dlc-composed-in")
            .process("always-fail", (io, c) -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("simulated persistent failure");
            })
            .toSink("dlc-composed-out")
            .withRetry(retry -> retry
                .maxAttempts(3)
                .onErrorChannel(err -> err.toChannel("dlc-composed-poison-queue")))
            .build();

        pipeliteContext.registerFlowDefinition(deadLetterQueue);
        pipeliteContext.registerFlowDefinition(mainFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("dlc-composed-in", exchangeFactory.createExchange("persistent-poison-payload"));

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
                .fromSource("duplicate-name-in")
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
     * receiving side here is a plain flow whose {@code fromSource("link://...")} matches the
     * target resource, exactly like any other channel-adapter delivery, not a
     * {@code tryFindFlowByName(...)} lookup.
     */
    @Test
    public void givenProtocolQualifiedTarget_whenProcessorThrows_thenDeliveredDirectlyToTheChannelAdapter(){

        final AtomicReference<ExchangeImpl> deadLettered = new AtomicReference<>();

        // No protocol here: an internal ("logic") source is what LinkChannelAdapter registers a
        // consumer for (see LinkChannelAdapter#onFlowRegistered) - link:// is only used on the
        // sending side (below), never on a receiving fromSource(...).
        final FlowDefinition deadLetterReceiver = Pipelite.defineFlow("link-qualified-dlc-receiver")
            .fromSource("link-qualified-dlc-poison-queue")
            .process("capture", (io, c) -> deadLettered.set((ExchangeImpl) io))
            .toSink("link-qualified-dlc-poison-out")
            .build();

        final FlowDefinition mainFlow = Pipelite.defineFlow("link-qualified-dlc-flow")
            .fromSource("link-qualified-dlc-in")
            .process("always-fail", (io, c) -> { throw new RuntimeException("boom"); })
            .toSink("link-qualified-dlc-out")
            .withErrorChannel(err -> err.toChannel("link://link-qualified-dlc-poison-queue"))
            .build();

        pipeliteContext.registerFlowDefinition(deadLetterReceiver);
        pipeliteContext.registerFlowDefinition(mainFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("link-qualified-dlc-in", exchangeFactory.createExchange("poison-payload"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> deadLettered.get() != null);
        assertEquals("poison-payload", deadLettered.get().getInputPayloadAs(String.class));
    }

    @Test
    public void givenPlainFlowName_whenToChannelIsCalled_thenAcceptedAndRedirectionAppliedAutomatically(){

        final AtomicReference<ExchangeImpl> deadLettered = new AtomicReference<>();

        final FlowDefinition deadLetterQueue = Pipelite.defineFlow("plain-name-dlc-poison-queue")
            .fromSource("plain-name-dlc-poison-queue")
            .process("capture", (io, c) -> deadLettered.set((ExchangeImpl) io))
            .toSink("plain-name-dlc-poison-out")
            .build();

        // Plain name, no protocol - resolved by its own defineFlow(...) name, not its
        // fromSource(...) resource (see the protocol-qualified case above for the other branch).
        final FlowDefinition mainFlow = Pipelite.defineFlow("plain-name-dlc-main-flow")
            .fromSource("plain-name-dlc-in")
            .process("always-fail", (io, c) -> { throw new RuntimeException("boom"); })
            .toSink("plain-name-dlc-out")
            .withErrorChannel(err -> err.toChannel("plain-name-dlc-poison-queue"))
            .build();

        pipeliteContext.registerFlowDefinition(deadLetterQueue);
        pipeliteContext.registerFlowDefinition(mainFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("plain-name-dlc-in", exchangeFactory.createExchange("poison-payload"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> deadLettered.get() != null);
        assertEquals("poison-payload", deadLettered.get().getInputPayloadAs(String.class));
    }

    @Test
    public void givenDeadLetterFlowNameDiffersFromItsSourceResource_whenToChannelIsCalled_thenResolvedByFlowNameNotSourceResource(){

        final AtomicReference<ExchangeImpl> deadLettered = new AtomicReference<>();

        // The dead-letter flow's own Pipelite.defineFlow(...) name is deliberately different from
        // its fromSource(...) resource, proving toChannel(...) resolves by flow identity, not
        // by the (unrelated) resource that flow happens to consume from.
        final FlowDefinition deadLetterQueue = Pipelite.defineFlow("distinct-flow-name-dlc-queue")
            .fromSource("totally-unrelated-source-resource")
            .process("capture", (io, c) -> deadLettered.set((ExchangeImpl) io))
            .toSink("distinct-flow-name-dlc-out")
            .build();

        final FlowDefinition mainFlow = Pipelite.defineFlow("distinct-flow-name-dlc-main-flow")
            .fromSource("distinct-flow-name-dlc-in")
            .process("always-fail", (io, c) -> { throw new RuntimeException("boom"); })
            .toSink("distinct-flow-name-dlc-out-2")
            .withErrorChannel(err -> err.toChannel("distinct-flow-name-dlc-queue"))
            .build();

        pipeliteContext.registerFlowDefinition(deadLetterQueue);
        pipeliteContext.registerFlowDefinition(mainFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("distinct-flow-name-dlc-in", exchangeFactory.createExchange("poison-payload"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> deadLettered.get() != null);
        assertEquals("poison-payload", deadLettered.get().getInputPayloadAs(String.class));
    }

}
