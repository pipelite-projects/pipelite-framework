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

import io.pipelite.common.support.Preconditions;
import io.pipelite.common.support.fs.LockedFileStore;
import io.pipelite.common.support.serialization.BaseEncoding;
import io.pipelite.spi.inbox.InboxEntry;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;

/**
 * Default {@link DurableInboxDeadLetterWriter}: one single, un-segmented, ever-appended file per
 * resource — {@code <sha256(resourceKey)>_dlq}, no numeric suffix, never rotated — under the same
 * shared directory the live inbox itself uses ({@code DefaultPipeliteContext}'s default is {@code
 * PipeliteHome.resolve("state/inbox")}, identical to {@code SegmentedLogDurableInboxProvider}'s
 * own base directory), written via {@link LockedFileStore}.
 * <p>
 * One growing file per resource rather than one file per entry, and rather than a separate
 * directory tree: a dead-lettered entry is expected to be rare (a payload class changed shape
 * between the run that wrote it and the run trying to read it back), so neither the one-file-
 * per-message volume concern that ruled out that shape for the live inbox (see the durable-inbox
 * design doc, §4), nor any need for segmentation/rotation, ever applies here — a single append
 * target per resource is simplest and keeps every dead letter for a given queue in one place.
 * {@link LockedFileStore} has no native append primitive, so an entry is appended via {@link
 * LockedFileStore#readAndWriteLocked}: read the file's current text (empty if new), concatenate
 * the new record's block, write the whole thing back — one lock held for the whole read-modify-
 * write, safe against concurrent dead-letter writes for the same resource. Cheap enough given how
 * rare a dead letter should be; not the write-amplification concern it would be on the live inbox's
 * hot path.
 * <p>
 * Each record is a self-contained {@link Properties} block ({@code resourceKey}, {@code entryId},
 * {@code failureTime}, the failing exception's class/message, every metadata entry - indexed
 * rather than used directly as a property key, since an entry's metadata key is arbitrary text
 * that might not be a valid property key - and the entry's own opaque payload bytes, Base64-
 * encoded so they fit in a text file next to everything else), separated from the next by a
 * blank line and a plain divider comment. Write-only by design (see this interface's own Javadoc)
 * — nothing in the framework parses this file back.
 */
public class FileDurableInboxDeadLetterWriter implements DurableInboxDeadLetterWriter {

    private static final String FILE_SUFFIX = "_dlq";
    private static final String RECORD_DIVIDER = "\n#---\n";

    private static final String RESOURCE_KEY_KEY = "resourceKey";
    private static final String ENTRY_ID_KEY = "entryId";
    private static final String FAILURE_TIME_KEY = "failureTime";
    private static final String CAUSE_CLASS_NAME_KEY = "causeClassName";
    private static final String CAUSE_MESSAGE_KEY = "causeMessage";
    private static final String METADATA_COUNT_KEY = "metadataCount";
    private static final String PAYLOAD_ENCODING_KEY = "payloadEncoding";
    private static final String PAYLOAD_KEY = "payload";
    private static final String BASE64_ENCODING = "base64";

    private final LockedFileStore store;

    public FileDurableInboxDeadLetterWriter(Path directory) {
        Preconditions.notNull(directory, "directory is required and cannot be null");
        this.store = new LockedFileStore(directory);
    }

    @Override
    public void write(String resourceKey, InboxEntry entry, Exception cause) {
        Preconditions.hasText(resourceKey, "resourceKey is required and cannot be null/empty");
        Preconditions.notNull(entry, "entry is required and cannot be null");
        Preconditions.notNull(cause, "cause is required and cannot be null");
        final String record = format(resourceKey, entry, cause);
        store.readAndWriteLocked(fileName(resourceKey), current ->
            current.map(existing -> existing + RECORD_DIVIDER + record).orElse(record));
    }

    private static String fileName(String resourceKey) {
        return sha256Hex(resourceKey) + FILE_SUFFIX;
    }

    private static String format(String resourceKey, InboxEntry entry, Exception cause) {

        final Properties properties = new Properties();
        properties.setProperty(RESOURCE_KEY_KEY, resourceKey);
        properties.setProperty(ENTRY_ID_KEY, entry.getId());
        properties.setProperty(FAILURE_TIME_KEY, LocalDateTime.now().toString());
        properties.setProperty(CAUSE_CLASS_NAME_KEY, cause.getClass().getName());
        if (cause.getMessage() != null) {
            properties.setProperty(CAUSE_MESSAGE_KEY, cause.getMessage());
        }

        int index = 0;
        for (Map.Entry<String, String> metadataEntry : entry.getMetadata().entrySet()) {
            properties.setProperty("metadata." + index + ".key", metadataEntry.getKey());
            properties.setProperty("metadata." + index + ".value", metadataEntry.getValue());
            index++;
        }
        properties.setProperty(METADATA_COUNT_KEY, String.valueOf(index));

        properties.setProperty(PAYLOAD_ENCODING_KEY, BASE64_ENCODING);
        properties.setProperty(PAYLOAD_KEY, BaseEncoding.base64().encode(entry.getPayload()));

        final StringWriter writer = new StringWriter();
        try {
            properties.store(writer, null);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to format dead-lettered inbox entry", exception);
        }
        return writer.toString();
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
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm not available", exception);
        }
    }

}
