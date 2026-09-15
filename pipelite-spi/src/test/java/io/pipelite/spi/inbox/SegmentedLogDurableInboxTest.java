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
package io.pipelite.spi.inbox;

import io.pipelite.spi.flow.exchange.IdentityGenerator;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class SegmentedLogDurableInboxTest {

    private static final String RESOURCE_PREFIX = "test-resource";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * Deterministic, human-readable ids instead of {@code DistributedIdentityGeneratorImpl}'s
     * real ones - makes assertions on which entry is which straightforward.
     */
    private static final class SequentialIdentityGenerator implements IdentityGenerator {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public long nextId() {
            return counter.incrementAndGet();
        }

        @Override
        public String nextIdAsText() {
            return "entry-" + counter.incrementAndGet();
        }
    }

    /**
     * The shared base directory every resource's segment files live under (see this class's own
     * Javadoc: no subdirectory per resource, files are distinguished by name prefix instead).
     */
    private Path directory() {
        return temporaryFolder.getRoot().toPath().resolve("inbox");
    }

    private SegmentedLogDurableInbox newInbox() {
        return new SegmentedLogDurableInbox(directory(), RESOURCE_PREFIX, new SequentialIdentityGenerator());
    }

    private SegmentedLogDurableInbox newInbox(long maxSegmentSizeBytes) {
        return new SegmentedLogDurableInbox(directory(), RESOURCE_PREFIX, new SequentialIdentityGenerator(), maxSegmentSizeBytes, true);
    }

    @Test
    public void shouldReturnEnqueuedEntryAsPending() {

        final DurableInbox inbox = newInbox();
        final String id = inbox.enqueue("payload-1".getBytes(StandardCharsets.UTF_8), Map.of("k", "v"));

        final List<InboxEntry> pending = inbox.pendingEntries();
        Assert.assertEquals(1, pending.size());
        Assert.assertEquals(id, pending.get(0).getId());
        Assert.assertArrayEquals("payload-1".getBytes(StandardCharsets.UTF_8), pending.get(0).getPayload());
        Assert.assertEquals("v", pending.get(0).getMetadata().get("k"));
    }

    @Test
    public void shouldRemoveFromPendingOnceAcknowledged() {

        final DurableInbox inbox = newInbox();
        final String id = inbox.enqueue("payload".getBytes(StandardCharsets.UTF_8), Map.of());

        inbox.acknowledge(id);

        Assert.assertTrue(inbox.pendingEntries().isEmpty());
    }

    @Test
    public void acknowledgeShouldBeIdempotent() {

        final DurableInbox inbox = newInbox();
        final String id = inbox.enqueue("payload".getBytes(StandardCharsets.UTF_8), Map.of());

        inbox.acknowledge(id);
        inbox.acknowledge(id); // must not throw

        Assert.assertTrue(inbox.pendingEntries().isEmpty());
    }

    @Test
    public void shouldPreserveEnqueueOrderInPendingEntries() {

        final DurableInbox inbox = newInbox();
        inbox.enqueue("first".getBytes(StandardCharsets.UTF_8), Map.of());
        inbox.enqueue("second".getBytes(StandardCharsets.UTF_8), Map.of());
        inbox.enqueue("third".getBytes(StandardCharsets.UTF_8), Map.of());

        final List<InboxEntry> pending = inbox.pendingEntries();
        Assert.assertEquals(3, pending.size());
        Assert.assertEquals("first", new String(pending.get(0).getPayload(), StandardCharsets.UTF_8));
        Assert.assertEquals("second", new String(pending.get(1).getPayload(), StandardCharsets.UTF_8));
        Assert.assertEquals("third", new String(pending.get(2).getPayload(), StandardCharsets.UTF_8));
    }

    @Test
    public void shouldIsolateEntriesByNamePrefixWithinTheSharedDirectory() {

        final IdentityGenerator identityGenerator = new SequentialIdentityGenerator();
        final DurableInbox inboxA = new SegmentedLogDurableInbox(directory(), "resource-a", identityGenerator);
        final DurableInbox inboxB = new SegmentedLogDurableInbox(directory(), "resource-b", identityGenerator);

        inboxA.enqueue("for-a".getBytes(StandardCharsets.UTF_8), Map.of());
        inboxB.enqueue("for-b-1".getBytes(StandardCharsets.UTF_8), Map.of());
        inboxB.enqueue("for-b-2".getBytes(StandardCharsets.UTF_8), Map.of());

        Assert.assertEquals(1, inboxA.pendingEntries().size());
        Assert.assertEquals(2, inboxB.pendingEntries().size());
        Assert.assertTrue("both instances' segment files must land in the same shared directory",
            Files.exists(directory().resolve("resource-a_00000000000000000000.log")));
        Assert.assertTrue(Files.exists(directory().resolve("resource-b_00000000000000000000.log")));
    }

    @Test
    public void shouldRecoverUnacknowledgedEntriesAfterRestart() {

        final Path directory = directory();
        final IdentityGenerator identityGenerator = new SequentialIdentityGenerator();

        final DurableInbox first = new SegmentedLogDurableInbox(directory, RESOURCE_PREFIX, identityGenerator);
        final String keptId = first.enqueue("kept".getBytes(StandardCharsets.UTF_8), Map.of());
        final String ackedId = first.enqueue("acked".getBytes(StandardCharsets.UTF_8), Map.of());
        first.acknowledge(ackedId);

        // Simulates a process restart: a brand new instance over the same directory/prefix,
        // nothing shared with `first` in memory.
        final DurableInbox afterRestart = new SegmentedLogDurableInbox(directory, RESOURCE_PREFIX, identityGenerator);
        final List<InboxEntry> pending = afterRestart.pendingEntries();

        Assert.assertEquals(1, pending.size());
        Assert.assertEquals(keptId, pending.get(0).getId());
        Assert.assertEquals("kept", new String(pending.get(0).getPayload(), StandardCharsets.UTF_8));
    }

    @Test
    public void shouldTruncateTornTailInsteadOfFailingRecovery() throws Exception {

        final Path directory = directory();
        final IdentityGenerator identityGenerator = new SequentialIdentityGenerator();

        final DurableInbox first = new SegmentedLogDurableInbox(directory, RESOURCE_PREFIX, identityGenerator);
        final String goodId = first.enqueue("good".getBytes(StandardCharsets.UTF_8), Map.of());

        // Simulate a crash mid-append: truncate the segment file so its last frame is torn
        // (append a few stray bytes that look like the start of another frame but aren't
        // complete - a short length prefix with no body/crc to follow).
        final Path segment = directory.resolve(RESOURCE_PREFIX + "_00000000000000000000.log");
        Assert.assertTrue(Files.exists(segment));
        try (RandomAccessFile raf = new RandomAccessFile(segment.toFile(), "rw")) {
            raf.seek(raf.length());
            raf.write(new byte[]{0, 0, 0, 100}); // claims a 100-byte frame that was never written
        }

        final DurableInbox afterCrash = new SegmentedLogDurableInbox(directory, RESOURCE_PREFIX, identityGenerator);
        final List<InboxEntry> pending = afterCrash.pendingEntries(); // must not throw

        Assert.assertEquals(1, pending.size());
        Assert.assertEquals(goodId, pending.get(0).getId());

        // The instance must still be writable after recovering from a torn tail.
        final String newId = afterCrash.enqueue("after-recovery".getBytes(StandardCharsets.UTF_8), Map.of());
        Assert.assertEquals(2, afterCrash.pendingEntries().size());
        Assert.assertNotEquals(goodId, newId);
    }

    @Test
    public void shouldDeleteFullyAcknowledgedNonCurrentSegment() {

        // A tiny max size forces a rollover after the very first frame.
        final DurableInbox inbox = newInbox(1L);

        final String firstId = inbox.enqueue("first".getBytes(StandardCharsets.UTF_8), Map.of());
        final String secondId = inbox.enqueue("second".getBytes(StandardCharsets.UTF_8), Map.of());

        final Path firstSegment = directory().resolve(RESOURCE_PREFIX + "_00000000000000000000.log");
        Assert.assertTrue("first segment should exist before it is fully acknowledged", Files.exists(firstSegment));

        inbox.acknowledge(firstId);

        Assert.assertFalse("first segment should be deleted once fully acknowledged and no longer current",
            Files.exists(firstSegment));
        Assert.assertEquals(1, inbox.pendingEntries().size());
        Assert.assertEquals(secondId, inbox.pendingEntries().get(0).getId());
    }

}
