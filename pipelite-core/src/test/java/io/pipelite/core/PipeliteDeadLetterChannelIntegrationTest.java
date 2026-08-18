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
import io.pipelite.core.definition.DuplicateProcessorNameException;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.Exchange;
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
 * Covers issue #5 ("[Core] Dead Letter channel (DLC)"): {@code .withErrorChannel(c ->
 * c.definedFlow(flowName))} usable independently of {@code .withRetryChannel(...)} (immediate
 * dead-lettering, no retry) and composed with it (retry first, dead-letter only once exhausted).
 */
public class PipeliteDeadLetterChannelIntegrationTest {

    private PipeliteContext pipeliteContext;

    @Before
    public void setup(){
        pipeliteContext = Pipelite.createContext();
    }

    @Test
    public void givenDeadLetterChannelAlone_whenProcessorThrows_thenRoutedImmediatelyWithNoRetry(){

        final AtomicInteger attemptCount = new AtomicInteger(0);
        final AtomicReference<Exchange> deadLettered = new AtomicReference<>();

        final FlowDefinition deadLetterQueue = Pipelite.defineFlow("dlc-only-poison-queue")
            .fromSource("dlc-only-poison-queue")
            .process("capture", (io, c) -> deadLettered.set((Exchange) io))
            .toSink("dlc-only-poison-out")
            .build();

        final FlowDefinition mainFlow = Pipelite.defineFlow("dlc-only-main-flow")
            .fromSource("dlc-only-in")
            .process("always-fail", (io, c) -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("simulated poison message");
            })
            .toSink("dlc-only-out")
            .withErrorChannel(c -> c.definedFlow("dlc-only-poison-queue"))
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
        final AtomicReference<Exchange> deadLettered = new AtomicReference<>();

        final FlowDefinition deadLetterQueue = Pipelite.defineFlow("dlc-composed-poison-queue")
            .fromSource("dlc-composed-poison-queue")
            .process("capture", (io, c) -> deadLettered.set((Exchange) io))
            .toSink("dlc-composed-poison-out")
            .build();

        final FlowDefinition mainFlow = Pipelite.defineFlow("dlc-composed-main-flow")
            .fromSource("dlc-composed-in")
            .process("always-fail", (io, c) -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("simulated persistent failure");
            })
            .toSink("dlc-composed-out")
            .withRetryChannel(retry -> retry.maxAttempts(3))
            .withErrorChannel(c -> c.definedFlow("dlc-composed-poison-queue"))
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
    public void givenRetryChannelAlone_whenAttemptsAreExhausted_thenMessageIsDroppedNotDeadLettered(){

        final AtomicInteger attemptCount = new AtomicInteger(0);

        final FlowDefinition mainFlow = Pipelite.defineFlow("retry-only-main-flow")
            .fromSource("retry-only-in")
            .process("always-fail", (io, c) -> {
                attemptCount.incrementAndGet();
                throw new RuntimeException("simulated persistent failure");
            })
            .toSink("retry-only-out")
            .withRetryChannel(retry -> retry.maxAttempts(2))
            .build();

        pipeliteContext.registerFlowDefinition(mainFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("retry-only-in", exchangeFactory.createExchange("poison-payload"));

        // Exactly maxAttempts attempts, then silence (documented fallback: drop, no DLC
        // configured) — proves the cap itself works, independent of dead-lettering.
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> attemptCount.get() == 2);
        try {
            Awaitility.await().atMost(2, TimeUnit.SECONDS).until(() -> attemptCount.get() > 2);
            fail("Expected no further attempts once maxAttempts is exhausted with no dead letter channel configured");
        } catch (org.awaitility.core.ConditionTimeoutException expected) {
            // exactly the documented fallback behavior
        }
        assertEquals(2, attemptCount.get());
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

    @Test
    public void givenKafkaProtocolQualifiedName_whenDefinedFlowIsCalled_thenThrowsIllegalArgumentException(){
        try {
            Pipelite.defineFlow("kafka-qualified-dlc-flow")
                .fromSource("kafka-qualified-dlc-in")
                .process("always-fail", (io, c) -> { throw new RuntimeException("boom"); })
                .toSink("kafka-qualified-dlc-out")
                .withErrorChannel(c -> c.definedFlow("kafka://poison-queue"))
                .build();
            fail("Expected IllegalArgumentException: definedFlow must never take a direct channel adapter URL");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("plain name"));
        }
    }

    @Test
    public void givenLinkProtocolQualifiedName_whenDefinedFlowIsCalled_thenThrowsIllegalArgumentException(){
        try {
            Pipelite.defineFlow("link-qualified-dlc-flow")
                .fromSource("link-qualified-dlc-in")
                .process("always-fail", (io, c) -> { throw new RuntimeException("boom"); })
                .toSink("link-qualified-dlc-out")
                .withErrorChannel(c -> c.definedFlow("link://poison-queue"))
                .build();
            fail("Expected IllegalArgumentException: definedFlow takes a plain flow name, not a link:// URL — " +
                "the redirection is applied automatically");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("plain name"));
        }
    }

    @Test
    public void givenPlainFlowName_whenDefinedFlowIsCalled_thenAcceptedAndRedirectionAppliedAutomatically(){

        final AtomicReference<Exchange> deadLettered = new AtomicReference<>();

        final FlowDefinition deadLetterQueue = Pipelite.defineFlow("plain-name-dlc-poison-queue")
            .fromSource("plain-name-dlc-poison-queue")
            .process("capture", (io, c) -> deadLettered.set((Exchange) io))
            .toSink("plain-name-dlc-poison-out")
            .build();

        // Plain name, no protocol - exactly what definedFlow expects; the target is resolved by
        // its own defineFlow(...) name, not its fromSource(...) resource.
        final FlowDefinition mainFlow = Pipelite.defineFlow("plain-name-dlc-main-flow")
            .fromSource("plain-name-dlc-in")
            .process("always-fail", (io, c) -> { throw new RuntimeException("boom"); })
            .toSink("plain-name-dlc-out")
            .withErrorChannel(c -> c.definedFlow("plain-name-dlc-poison-queue"))
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
    public void givenDeadLetterFlowNameDiffersFromItsSourceResource_whenDefinedFlowIsCalled_thenResolvedByFlowNameNotSourceResource(){

        final AtomicReference<Exchange> deadLettered = new AtomicReference<>();

        // The dead-letter flow's own Pipelite.defineFlow(...) name is deliberately different from
        // its fromSource(...) resource, proving definedFlow(...) resolves by flow identity, not
        // by the (unrelated) resource that flow happens to consume from.
        final FlowDefinition deadLetterQueue = Pipelite.defineFlow("distinct-flow-name-dlc-queue")
            .fromSource("totally-unrelated-source-resource")
            .process("capture", (io, c) -> deadLettered.set((Exchange) io))
            .toSink("distinct-flow-name-dlc-out")
            .build();

        final FlowDefinition mainFlow = Pipelite.defineFlow("distinct-flow-name-dlc-main-flow")
            .fromSource("distinct-flow-name-dlc-in")
            .process("always-fail", (io, c) -> { throw new RuntimeException("boom"); })
            .toSink("distinct-flow-name-dlc-out-2")
            .withErrorChannel(c -> c.definedFlow("distinct-flow-name-dlc-queue"))
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
