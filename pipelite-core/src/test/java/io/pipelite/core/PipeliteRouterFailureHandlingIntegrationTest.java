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
import io.pipelite.spi.context.IOKeys;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.HeadersImpl;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Issue #105: a failure while routing reaches the flow's exception handler, so the failure entry
 * points of #91 protect the routing step like any other. The destination is computed from a header
 * on purpose: a destination written in the DSL is checked when the context starts (#88), one that
 * is only known when the exchange is routed cannot be.
 */
public class PipeliteRouterFailureHandlingIntegrationTest {

    private static final String NOBODY = "queue://nobody-declares-this";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String previousHome;
    private ConfigurablePipeliteContext pipeliteContext;

    @Before
    public void setup() throws Exception {
        previousHome = System.getProperty("pipelite.home");
        System.setProperty("pipelite.home", temporaryFolder.newFolder("home").toPath().toString());
        pipeliteContext = (ConfigurablePipeliteContext) Pipelite.createContext();
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

    private void supplyRoutedTo(String source, String destination) {
        final HeadersImpl headers = new HeadersImpl();
        headers.putHeader("destination", destination);
        pipeliteContext.supplyExchange("queue://" + source, pipeliteContext.getExchangeFactory().createExchange(headers, "payload"));
    }

    @Test
    public void givenARouteThatCannotBeDelivered_thenTheFlowsExceptionHandlerIsCalledWithTheRouterAsFailedProcessor() {

        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final AtomicReference<String> failedProcessor = new AtomicReference<>();
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("router-flow")
            .fromSource("queue://router-in")
            .toRoute(routes -> routes.dynamic()
                .when("Headers['x'] == 'y'").then("#{Headers['destination']}")
                .otherwise("#{Headers['destination']}")
                .end())
            .withExceptionHandler((exception, exchange) -> {
                failedProcessor.set(((ExchangeImpl) exchange).getProperty(IOKeys.FLOW_EXECUTION_FAILED_PROCESSOR_PROPERTY_NAME, String.class));
                failure.set(exception);
            })
            .build());
        pipeliteContext.start();

        supplyRoutedTo("router-in", NOBODY);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> failure.get() != null);
        Assert.assertEquals("to-route", failedProcessor.get());
        Assert.assertTrue(failure.get().getMessage(), failure.get().getMessage().contains(NOBODY));
    }

    @Test
    public void givenARouteThatCannotBeDeliveredAndARetry_thenTheRetryResumesAtTheRouterUntilExhausted() {

        final AtomicInteger stepRuns = new AtomicInteger(0);
        final AtomicReference<Throwable> exhausted = new AtomicReference<>();
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("retrying-router-flow")
            .fromSource("queue://retrying-router-in")
            .process("count", (io, c) -> stepRuns.incrementAndGet())
            .toRoute(routes -> routes.dynamic()
                .when("Headers['x'] == 'y'").then("#{Headers['destination']}")
                .otherwise("#{Headers['destination']}")
                .end())
            .withRetry(retry -> retry
                .maxAttempts(2)
                .onExceptionHandler((exception, exchange) -> exhausted.set(exception)))
            .build());
        pipeliteContext.start();

        supplyRoutedTo("retrying-router-in", NOBODY);

        Awaitility.await().atMost(30, TimeUnit.SECONDS).until(() -> exhausted.get() != null);
        // Resumed at the failed step, not from the source: the step before the router ran once
        Assert.assertEquals(1, stepRuns.get());
    }

}
