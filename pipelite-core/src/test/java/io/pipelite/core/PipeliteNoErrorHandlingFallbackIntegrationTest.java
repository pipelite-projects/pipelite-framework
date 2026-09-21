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
import io.pipelite.dsl.ChannelProtocols;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regression coverage for issue #87: a flow declaring neither {@code .withRetryChannel(...)} nor
 * {@code .withErrorChannel(...)} previously had no well-defined fallback - a processor-level
 * exception propagated all the way up to the dispatch thread, and (with the durable inbox enabled,
 * the default) never reached {@code EventDrivenConsumer#dispatchToNext}'s own {@code
 * acknowledge(...)} call, leaving the entry permanently pending and therefore redelivered - and
 * re-failing identically - on every future restart. {@link
 * io.pipelite.core.flow.GlobalDefaultExceptionHandler} closes that gap by giving every such flow a
 * real (if minimal) handler instead of no handler at all.
 */
public class PipeliteNoErrorHandlingFallbackIntegrationTest {

    private PipeliteContext pipeliteContext;

    @Before
    public void setup() {
        pipeliteContext = Pipelite.createContext();
    }

    @Test
    public void givenNoRetryChannelAndNoErrorChannel_whenAProcessorThrows_thenTheExchangeIsResolvedNotLeftPending() {

        final String resource = "no-error-handling-in";
        final AtomicInteger invocationCount = new AtomicInteger(0);

        final FlowDefinition testFlow = Pipelite.defineFlow("no-error-handling-flow")
            .fromSource(resource)
            .process("always-fail", (io, c) -> {
                invocationCount.incrementAndGet();
                throw new RuntimeException("simulated failure - no retry/error channel configured");
            })
            .toSink("no-error-handling-out")
            .build();

        pipeliteContext.registerFlowDefinition(testFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange(ChannelProtocols.linkURL(resource), exchangeFactory.createExchange("poison-payload"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> invocationCount.get() == 1);

        // Before the #87 fix, this would time out: with no handler at all, the exception
        // propagated past dispatchToNext's acknowledge(...) call and the entry stayed pending
        // forever - only a restart (never this same run) would ever attempt it again.
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() ->
            pipeliteContext.getDurableInboxProvider().forResource(resource).pendingEntries().isEmpty());

        // Exactly one attempt - no retry was configured, so none should have happened.
        Assert.assertEquals(1, invocationCount.get());
    }

    @Test
    public void givenNoRetryChannelAndNoErrorChannel_whenAProcessorThrows_thenTheFlowKeepsProcessingLaterExchanges() {

        final String resource = "no-error-handling-survives-in";
        final AtomicInteger invocationCount = new AtomicInteger(0);
        final AtomicInteger successCount = new AtomicInteger(0);

        final FlowDefinition testFlow = Pipelite.defineFlow("no-error-handling-survives-flow")
            .fromSource(resource)
            .process("fail-once-then-succeed", (io, c) -> {
                if (invocationCount.incrementAndGet() == 1) {
                    throw new RuntimeException("simulated failure - no retry/error channel configured");
                }
                successCount.incrementAndGet();
            })
            .toSink("no-error-handling-survives-out")
            .build();

        pipeliteContext.registerFlowDefinition(testFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange(ChannelProtocols.linkURL(resource), exchangeFactory.createExchange("poison-payload"));
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> invocationCount.get() == 1);

        // The dispatch thread must have survived the unhandled failure above to process this one.
        pipeliteContext.supplyExchange(ChannelProtocols.linkURL(resource), exchangeFactory.createExchange("good-payload"));
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> successCount.get() == 1);
    }

}
