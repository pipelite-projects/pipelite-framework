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

import io.pipelite.components.http.config.HttpChannelConfigurer;
import io.pipelite.core.context.ConfigurablePipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Issue #54: {@code DefaultPipeliteContext.notifyContextStarted()} used to run after {@code
 * serviceManager.startServices()} had already started every flow's consumer threads, with no
 * rollback if a {@code ContextEventListener.onContextStarted()} (here, {@code
 * HttpChannelAdapter}'s own) threw - the caller saw a failed {@code start()} and reasonably
 * assumed nothing was active, while the queue flow below was, in fact, still consuming.
 * <p>
 * The HTTP port is deliberately pre-occupied by an unrelated {@link ServerSocket} so Undertow's
 * own {@code start()} fails for a completely realistic reason (a real bind failure), not a fake
 * test-only listener.
 */
public class PipeliteContextStartRollbackIntegrationTest {

    private ConfigurablePipeliteContext context;
    private ServerSocket occupyingSocket;

    @After
    public void tearDown() throws IOException {
        if (context != null) {
            try {
                context.stop();
            } catch (RuntimeException ignored) {
                // Already stopped (or never fully started) by the rollback under test - a second
                // stop() here is only best-effort cleanup, not part of what this test asserts.
            }
        }
        if (occupyingSocket != null) {
            occupyingSocket.close();
        }
    }

    @Test
    public void givenAChannelAdapterFailsOnContextStarted_thenStartRollsBackTheAlreadyStartedServices() throws Exception {

        final int httpPort = findFreePort();
        occupyingSocket = new ServerSocket(httpPort);

        final AtomicInteger processedCount = new AtomicInteger();
        // durableInbox=false on both: this test's own point is service lifecycle, not delivery
        // durability, and a durable, unacknowledged entry surviving under the real ~/.pipelite
        // (no per-test isolation exists for it) would otherwise get replayed on a later run and
        // silently inflate processedCount before the scenario below even starts.
        final FlowDefinition queueFlow = Pipelite.defineFlow("issue54-queue-flow")
            .fromSource("queue://issue54-ingress?durableInbox=false")
            .process("count", (exchange, contribution) -> processedCount.incrementAndGet())
            .build();
        final FlowDefinition httpFlow = Pipelite.defineFlow("issue54-http-flow")
            .fromSource("http://issue54-ingress?durableInbox=false")
            .process("noop", (exchange, contribution) -> { })
            .build();

        context = (ConfigurablePipeliteContext) Pipelite.createContext();
        context.addChannelConfigurer((HttpChannelConfigurer) configuration -> configuration.setPort(httpPort));
        context.registerFlowDefinition(queueFlow);
        context.registerFlowDefinition(httpFlow);

        try {
            context.start();
            Assert.fail("expected start() to fail: the HTTP port is already occupied");
        } catch (RuntimeException expected) {
            // Undertow itself fails to bind the already-occupied port.
        }

        // If the queue flow's consumer were still running (the bug), this would be processed
        // almost immediately; a rolled-back context has nothing left listening for it.
        context.supplyExchange("queue://issue54-ingress", context.getExchangeFactory().createExchange("test"));
        Thread.sleep(300);

        Assert.assertEquals("a failed start() must leave no consumer thread running", 0, processedCount.get());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

}
