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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Issue #108: a source resource is an address, not the identity of a flow - {@code http://orders},
 * an internal {@code orders} and two flows on one Kafka topic all share one - so what belongs to a
 * flow (its durable inbox, the resumption of its retries) is keyed by the flow, and two flows that
 * share a resource do not interfere. The two flows here share {@code shared-name}: one reads it
 * from a {@code time://} source, the other is the internal flow behind {@code link://shared-name}.
 * The {@code time://} flow is registered first on purpose, so it is the one a lookup by resource
 * would find.
 */
public class PipeliteFlowsSharingASourceResourceIntegrationTest {

    private static final String SHARED = "shared-name";
    private static final String TIME_SOURCE = "time://" + SHARED + "?period=3600000&timeUnit=MILLISECONDS";

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
    public void givenTwoFlowsSharingAResource_whenAStepFails_thenTheRetryResumesOnTheFlowThatFailedAtTheFailedStep() {

        final AtomicInteger countRuns = new AtomicInteger(0);
        final AtomicInteger flakyRuns = new AtomicInteger(0);
        final AtomicBoolean done = new AtomicBoolean(false);

        final DefaultPipeliteContext context = new DefaultPipeliteContext();
        context.setFlowExecutionDumpRepository(new FlowExecutionDumpInMemoryRepository());
        context.registerFlowDefinition(Pipelite.defineFlow("time-flow")
            .fromSource(TIME_SOURCE)
            .process("noop", (io, c) -> { })
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("internal-flow")
            .fromSource(SHARED)
            .process("count", (io, c) -> countRuns.incrementAndGet())
            .process("flaky", (io, c) -> {
                if (flakyRuns.incrementAndGet() < 2) {
                    throw new RuntimeException("first attempt fails");
                }
            })
            .process("done", (io, c) -> done.set(true))
            .withRetry(retry -> retry.maxAttempts(3).onExceptionHandler((exception, exchange) -> { }))
            .build());
        context.start();
        try {
            context.supplyExchange("link://" + SHARED, context.getExchangeFactory().createExchange("payload"));

            Awaitility.await().atMost(20, TimeUnit.SECONDS).until(done::get);

            // Resumed at the step that failed, on the flow that failed. Looked up by the resource
            // both flows share, it used to land on time-flow, which has no "flaky" step, and fall
            // back to resupplying the source: "count" ran a second time.
            Assert.assertEquals(1, countRuns.get());
            Assert.assertEquals(2, flakyRuns.get());
        } finally {
            context.stop();
        }
    }

    @Test
    public void givenTwoFlowsSharingAResource_whenTheContextStarts_thenEachRecoversOnlyItsOwnInboxEntries() throws Exception {

        // Seeded before any context exists, through the same provider the framework uses: one
        // pending entry in the inbox of each flow. This instance is never touched again once the
        // context below creates its own (two live instances over one file must not write at once).
        final Path inboxDirectory = temporaryFolder.getRoot().toPath().resolve("state").resolve("inbox");
        final SegmentedLogDurableInboxProvider seedProvider =
            new SegmentedLogDurableInboxProvider(inboxDirectory, new DistributedIdentityGeneratorImpl());
        seedProvider.forFlow("internal-flow").enqueue(payloadOf("for-internal"), Map.of());
        seedProvider.forFlow("time-flow").enqueue(payloadOf("for-time"), Map.of());

        final List<String> processedByInternalFlow = new CopyOnWriteArrayList<>();
        final DefaultPipeliteContext context = new DefaultPipeliteContext();
        context.registerFlowDefinition(Pipelite.defineFlow("time-flow")
            .fromSource(TIME_SOURCE)
            .process("noop", (io, c) -> { })
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("internal-flow")
            .fromSource(SHARED)
            .process("record", (io, c) -> {
                if (io.getInputPayload() instanceof String payload) {
                    processedByInternalFlow.add(payload);
                }
            })
            .build());
        context.start();
        try {
            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() ->
                context.getDurableInboxProvider().forFlow("internal-flow").pendingEntries().isEmpty());

            // Only its own entry: with one inbox per resource, "for-time" would have been handed to
            // this flow as well.
            Assert.assertEquals(List.of("for-internal"), processedByInternalFlow);
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
