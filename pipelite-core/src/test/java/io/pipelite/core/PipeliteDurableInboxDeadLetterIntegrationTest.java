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

import io.pipelite.dsl.ChannelProtocols;
import io.pipelite.common.support.serialization.ObjectToByteArrayConverter;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import io.pipelite.spi.inbox.DurableInbox;
import io.pipelite.spi.inbox.SegmentedLogDurableInboxProvider;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Proves the fix for a real gap found while discussing serialization fragility for issue #70: a
 * durable-inbox entry whose payload can no longer be deserialized (e.g. a payload class changed
 * shape between the run that wrote it and this one - the expected, not exceptional, case during
 * iterative development) must not abort recovery for every other entry, nor prevent the whole
 * {@code PipeliteContext} from starting at all. Instead it is written out via a {@code
 * DurableInboxDeadLetterWriter} (default: {@code FileDurableInboxDeadLetterWriter}) and
 * acknowledged, so it stops being handed back as pending on every future restart.
 */
public class PipeliteDurableInboxDeadLetterIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String SOURCE = "deadletter-test-source";
    private static final String FLOW_NAME = "deadletter-test-flow";

    @Test
    public void givenAPoisonedEntryAlongsideAGoodOne_whenTheContextStarts_thenTheGoodOneRecoversAndThePoisonedOneIsDeadLettered() throws IOException {

        final String previousHome = System.getProperty("pipelite.home");
        System.setProperty("pipelite.home", temporaryFolder.getRoot().toPath().toString());
        try {

            // Seed both entries directly through the same DurableInbox shape the framework itself
            // uses, before any PipeliteContext exists - this instance is never touched again once
            // the context below creates its own (see SegmentedLogDurableInbox's own Javadoc on why
            // two live instances over the same file must not write concurrently).
            final Path inboxDirectory = temporaryFolder.getRoot().toPath().resolve("state").resolve("inbox");
            final DurableInbox seedInbox = new SegmentedLogDurableInboxProvider(inboxDirectory, new DistributedIdentityGeneratorImpl())
                .forFlow(FLOW_NAME);
            // Not a valid Java-serialized Exchange at all - stands in for a payload class whose
            // shape changed since this entry was written.
            seedInbox.enqueue("not a valid java-serialized exchange".getBytes(StandardCharsets.UTF_8), Map.of());
            final byte[] goodPayload = new ObjectToByteArrayConverter().convert(new ExchangeImpl(new SimpleMessage("order-good")));
            seedInbox.enqueue(goodPayload, Map.of());

            final List<String> processed = new CopyOnWriteArrayList<>();
            final DefaultPipeliteContext context = new DefaultPipeliteContext();
            final FlowDefinition flow = Pipelite.defineFlow(FLOW_NAME)
                .fromSource(ChannelProtocols.queueURL(SOURCE))
                .process("record", (ioContext, contribution) -> {
                    final Object payload = ioContext.getInputPayloadAs(Object.class);
                    processed.add(payload == null ? "null" : payload.toString());
                })
                .build();
            context.registerFlowDefinition(flow);

            // Must not throw despite the poisoned entry sitting right next to a good one - this is
            // the actual bug: today an unhandled deserialization failure here would propagate out
            // of registerFlows() and prevent the whole context from starting.
            context.start();

            try {
                // Synchronous by the time start() returns: recovery (including the dead-letter
                // write for the poisoned entry) runs entirely inside registerFlows(), before
                // serviceManager.startServices() - see DefaultPipeliteContext's own comment on why.
                // Same shared directory as the live inbox (state/inbox), not a separate one - the
                // dead letter's own <hash>_dlq file name can't collide with a live segment's.
                Assert.assertEquals("the poisoned entry must be dead-lettered into one _dlq file",
                    1, countFiles(inboxDirectory, "_dlq"));

                // Asynchronous: the good entry is only acknowledged once its resupplied Exchange
                // actually finishes dispatch.
                Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processed.size() >= 1);
                Assert.assertEquals(1, processed.size());

                Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() ->
                    context.getDurableInboxProvider().forFlow(FLOW_NAME).pendingEntries().isEmpty());
            } finally {
                context.stop();
            }

        } finally {
            if (previousHome != null) {
                System.setProperty("pipelite.home", previousHome);
            } else {
                System.clearProperty("pipelite.home");
            }
        }
    }

    private static long countFiles(Path directory, String extension) throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(extension)).count();
        }
    }

}
