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

import io.pipelite.common.support.serialization.ObjectToByteArrayConverter;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import io.pipelite.spi.inbox.SegmentedLogDurableInboxProvider;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Issue #115: an exchange recovered from the durable inbox onto a {@code time://} source used to
 * sit on {@code TimePollingConsumer}'s queue forever - {@code receive(long)} never looked at it and
 * produced a fresh tick unconditionally on every call - so it was never executed and never
 * acknowledged, and was recovered again, and lost again, on every restart.
 */
public class PipeliteTimeSourceInboxRecoveryIntegrationTest {

    private static final String FLOW_NAME = "time-flow";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String previousHome;

    @Before
    public void setup() {
        previousHome = System.getProperty("pipelite.home");
        System.setProperty("pipelite.home", temporaryFolder.getRoot().toPath().toString());
    }

    @After
    public void tearDown() {
        if (previousHome != null) {
            System.setProperty("pipelite.home", previousHome);
        } else {
            System.clearProperty("pipelite.home");
        }
    }

    @Test
    public void givenAPendingInboxEntry_whenATimeSourceContextStarts_thenItIsExecutedAndAcknowledged() {

        // Seeded before any context exists, through the same provider the framework uses.
        final Path inboxDirectory = temporaryFolder.getRoot().toPath().resolve("state").resolve("inbox");
        final SegmentedLogDurableInboxProvider seedProvider =
            new SegmentedLogDurableInboxProvider(inboxDirectory, new DistributedIdentityGeneratorImpl());
        seedProvider.forFlow(FLOW_NAME).enqueue(payloadOf("recovered-payload"), Map.of());

        final List<Object> processed = new CopyOnWriteArrayList<>();
        final DefaultPipeliteContext context = new DefaultPipeliteContext();
        context.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());
        context.registerFlowDefinition(Pipelite.defineFlow(FLOW_NAME)
            // durableInbox explicitly opted back in (issue #120 flipped time://'s own default to
            // off, since a plain tick is always regenerable) - this test's whole point is a source
            // that actually tracks entries durably, to prove recovery from them works.
            .fromSource("time://tick?period=200&timeUnit=MILLISECONDS&durableInbox=true")
            .process("record", (io, c) -> processed.add(io.getInputPayload()))
            .build());
        context.start();
        try {
            // The recovered payload, not a tick: it must be executed once the fix is in, and its
            // entry must be acknowledged, not just recovered and left on the queue forever.
            Awaitility.await().atMost(10, TimeUnit.SECONDS)
                .until(() -> processed.contains("recovered-payload"));

            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() ->
                context.getDurableInboxProvider().forFlow(FLOW_NAME).pendingEntries().isEmpty());

            // Ticks (LocalDateTime payloads) still run alongside it: the fix does not turn the
            // source into a pure queue consumer.
            Assert.assertTrue(processed.stream().anyMatch(LocalDateTime.class::isInstance));
        } finally {
            context.stop();
        }
    }

    private static byte[] payloadOf(String payload) {
        final SimpleMessage message = new SimpleMessage("id-" + payload);
        message.setPayload(payload);
        return new ObjectToByteArrayConverter().convert(new ExchangeImpl(message));
    }

}
