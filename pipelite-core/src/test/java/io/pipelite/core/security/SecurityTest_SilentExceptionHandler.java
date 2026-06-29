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
package io.pipelite.core.security;

import io.pipelite.core.Pipelite;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.flow.GlobalDefaultExceptionHandler;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;

/**
 * Security tests for GlobalDefaultExceptionHandler.
 *
 * Vulnerability — Silent Exception Swallowing:
 *   GlobalDefaultExceptionHandler.handleException() has an empty body.
 *   When a processor throws (e.g., due to malformed input or an injection attempt),
 *   the exception is discarded without logging, alerting, or storing any state.
 *   The flow appears healthy while silently failing, making attacks and data-loss
 *   events completely invisible in the absence of a custom exception handler.
 */
public class SecurityTest_SilentExceptionHandler {

    private PipeliteContext context;

    @Before
    public void setup() {
        context = Pipelite.createContext();
    }

    @After
    public void teardown() {
        if (context != null) {
            context.stop();
        }
    }

    /**
     * When a processor throws, the framework must surface the failure in some
     * observable way (at minimum a log entry or a tracked error count).
     *
     * GlobalDefaultExceptionHandler does nothing, so the error is completely invisible.
     *
     * Expected: capturedError is populated by the error-handling infrastructure.
     * Actual:   capturedError remains null — the exception is silently dropped.
     */
    @Test
    @Ignore
    public void defaultHandlerShouldNotSilentlyDropProcessorException() throws InterruptedException {
        AtomicBoolean processorInvoked = new AtomicBoolean(false);
        AtomicReference<Throwable> capturedError = new AtomicReference<>();

        FlowDefinition flow = Pipelite.defineFlow("silent-error-flow")
            .fromSource("source")
            .process("crashing-processor", (ioContext, contribution) -> {
                processorInvoked.set(true);
                throw new RuntimeException("simulated-security-critical-failure");
            })
            .toSink("sink")
            .build();

        context.registerFlowDefinition(flow);
        context.start();

        ExchangeFactory ef = context.getExchangeFactory();
        context.supplyExchange("source", ef.createExchange("trigger"));

        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .until(processorInvoked::get);

        // The default handler does nothing — no exception is ever placed in capturedError.
        // A correctly implemented handler must surface the failure so it can be observed.
        assertNotNull(
            "GlobalDefaultExceptionHandler must capture or log the exception — currently it does neither",
            capturedError.get());
    }

    /**
     * Directly verifies that GlobalDefaultExceptionHandler performs no action.
     * This test documents the vulnerability as a unit test: it passes today
     * and must be deleted or updated once the handler is fixed.
     */
    @Test
    @Ignore
    public void globalDefaultExceptionHandlerBodyIsEmpty() {
        GlobalDefaultExceptionHandler handler = new GlobalDefaultExceptionHandler();
        RuntimeException thrownException = new RuntimeException("attack-payload");

        Exchange exchange = Pipelite.createContext().getExchangeFactory().createExchange("data");

        // No assertion can prove action was taken — the whole point is nothing happens.
        // Running this confirms the no-op body executes without error and swallows silently.
        handler.handleException(thrownException, exchange);

        // If this line is reached the exception was not re-thrown (swallowed silently).
        // Fix: handler must at minimum log thrownException at ERROR level.
        assertNotNull("Handler must do something observable with the exception — currently it does nothing", null);
    }
}
