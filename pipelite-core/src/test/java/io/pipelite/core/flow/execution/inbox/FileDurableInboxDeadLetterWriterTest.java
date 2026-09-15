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
package io.pipelite.core.flow.execution.inbox;

import io.pipelite.common.support.serialization.BaseEncoding;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.inbox.DurableInbox;
import io.pipelite.spi.inbox.InboxEntry;
import io.pipelite.spi.inbox.SegmentedLogDurableInbox;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

public class FileDurableInboxDeadLetterWriterTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path directory() {
        // Same shared directory the live inbox itself uses in production - a dead letter's file
        // name (<hash>_dlq) can't collide with a live segment's (<hash>_<seq>.log).
        return temporaryFolder.getRoot().toPath().resolve("inbox");
    }

    /**
     * A real {@link InboxEntry}, obtained the only way possible from outside {@code
     * io.pipelite.spi.inbox} - the interface is sealed to framework implementations only. Each
     * call gets its own random name prefix so independent calls never share a segment file -
     * otherwise a later call's {@code pendingEntries()} would also see an earlier call's
     * still-pending entry, and {@code .get(0)} would return the wrong one.
     */
    private InboxEntry newPendingEntry(byte[] payload, Map<String, String> metadata) {
        final DurableInbox inbox = new SegmentedLogDurableInbox(
            directory(), "resource-" + java.util.UUID.randomUUID(), new DistributedIdentityGeneratorImpl());
        inbox.enqueue(payload, metadata);
        return inbox.pendingEntries().get(0);
    }

    @Test
    public void shouldWriteResourceEntryCauseMetadataAndPayload() throws IOException {

        final byte[] payload = "not a valid java-serialized exchange".getBytes(StandardCharsets.UTF_8);
        final InboxEntry entry = newPendingEntry(payload, Map.of(
            "exchangeId", "order-42", "flowName", "kitchen-processing-flow"));

        final FileDurableInboxDeadLetterWriter writer = new FileDurableInboxDeadLetterWriter(directory());
        final RuntimeException cause = new RuntimeException("simulated deserialization failure");

        writer.write("kitchen-start", entry, cause);

        final List<Properties> records = readDlqFile("kitchen-start");
        Assert.assertEquals(1, records.size());
        final Properties properties = records.get(0);

        Assert.assertEquals("kitchen-start", properties.getProperty("resourceKey"));
        Assert.assertEquals(entry.getId(), properties.getProperty("entryId"));
        Assert.assertNotNull("failureTime must be recorded", properties.getProperty("failureTime"));
        Assert.assertEquals(RuntimeException.class.getName(), properties.getProperty("causeClassName"));
        Assert.assertEquals("simulated deserialization failure", properties.getProperty("causeMessage"));

        Assert.assertEquals(Map.of("exchangeId", "order-42", "flowName", "kitchen-processing-flow"),
            readIndexedMetadata(properties));

        Assert.assertEquals("base64", properties.getProperty("payloadEncoding"));
        Assert.assertArrayEquals(payload, BaseEncoding.base64().decode(properties.getProperty("payload")));
    }

    @Test
    public void shouldOmitCauseMessageWhenNull() throws IOException {

        final InboxEntry entry = newPendingEntry("payload".getBytes(StandardCharsets.UTF_8), Map.of());
        final FileDurableInboxDeadLetterWriter writer = new FileDurableInboxDeadLetterWriter(directory());

        writer.write("some-resource", entry, new RuntimeException());

        final Properties properties = readDlqFile("some-resource").get(0);
        Assert.assertNull(properties.getProperty("causeMessage"));
        Assert.assertEquals("0", properties.getProperty("metadataCount"));
    }

    @Test
    public void shouldAppendSubsequentEntriesForTheSameResourceToTheSameSingleUnsegmentedFile() throws IOException {

        final InboxEntry first = newPendingEntry("first".getBytes(StandardCharsets.UTF_8), Map.of());
        final InboxEntry second = newPendingEntry("second".getBytes(StandardCharsets.UTF_8), Map.of());
        final FileDurableInboxDeadLetterWriter writer = new FileDurableInboxDeadLetterWriter(directory());

        writer.write("same-resource", first, new RuntimeException("first failure"));
        writer.write("same-resource", second, new RuntimeException("second failure"));

        // Exactly one file for the resource - not one per entry, not segmented/numbered.
        try (Stream<Path> files = Files.list(directory())) {
            Assert.assertEquals(1, files.filter(p -> p.getFileName().toString().endsWith("_dlq")).count());
        }

        final List<Properties> records = readDlqFile("same-resource");
        Assert.assertEquals(2, records.size());
        Assert.assertEquals(first.getId(), records.get(0).getProperty("entryId"));
        Assert.assertEquals("first failure", records.get(0).getProperty("causeMessage"));
        Assert.assertEquals(second.getId(), records.get(1).getProperty("entryId"));
        Assert.assertEquals("second failure", records.get(1).getProperty("causeMessage"));
    }

    /**
     * Reads the single {@code <sha256(resourceKey)>_dlq} file and splits it back into its
     * individual appended records - mirrors {@code FileDurableInboxDeadLetterWriter}'s own
     * divider, since nothing in production ever needs to parse this file back (write-only by
     * design).
     */
    private List<Properties> readDlqFile(String resourceKey) throws IOException {
        final Path file = directory().resolve(sha256Hex(resourceKey) + "_dlq");
        Assert.assertTrue("expected a dead-letter file for " + resourceKey, Files.exists(file));
        final String content = Files.readString(file);
        final List<Properties> records = new java.util.ArrayList<>();
        for (String block : content.split("\n#---\n")) {
            final Properties properties = new Properties();
            properties.load(new StringReader(block));
            records.add(properties);
        }
        return records;
    }

    private static Map<String, String> readIndexedMetadata(Properties properties) {
        final int count = Integer.parseInt(properties.getProperty("metadataCount"));
        final Map<String, String> metadata = new HashMap<>();
        for (int i = 0; i < count; i++) {
            metadata.put(properties.getProperty("metadata." + i + ".key"), properties.getProperty("metadata." + i + ".value"));
        }
        return metadata;
    }

    private static String sha256Hex(String value) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            final StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

}
