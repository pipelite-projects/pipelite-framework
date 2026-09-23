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

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.CRC32;

/**
 * Default local {@link DurableInbox} implementation: one append-only, segmented binary log per
 * source resource (an instance is always bound to exactly one resource — see this interface's own
 * Javadoc). All resources share one flat directory — {@code directory.resolve(namePrefix +
 * "_" + segmentSeq + ".inbox")} — mirroring {@code FileTailStateStore}'s own convention (one
 * directory, files named by a hash of the resource string) rather than nesting a subdirectory per
 * resource, which would buy nothing: contention, blast radius, and lifecycle are already scoped
 * per <em>file</em> (or file-prefix group), not per directory. Not one-file-per-entry like {@code
 * FileFlowExecutionDumpRepository} (issue #68) — that shape is fine for a low-volume retry
 * backlog, wrong for a structure that sees a source's <em>entire</em> message volume (see the
 * design doc behind issue #70).
 * <p>
 * {@code namePrefix} must be a stable, deterministic function of the flow name (see {@code
 * SegmentedLogDurableInboxProvider}, which hashes it the same way {@code FileTailStateStore}
 * does) — not a fresh random value per run: recovery after a restart depends on resolving the
 * exact same prefix for the same resource, with no separate index to look it up by if that
 * mapping isn't reproducible on its own.
 * <p>
 * <strong>Frame format</strong>, appended sequentially to whichever segment file is currently
 * open (never rewritten, only appended to — even acknowledgment is an appended tombstone frame,
 * not an edit of the original record): {@code [4B length of (type+body)][1B type][body][4B
 * CRC32 of (type+body)]}. Binary, length-prefixed — not text/delimited — because {@code body}
 * carries a Java-serialized {@code Exchange}, which can contain arbitrary byte values. The CRC
 * lets recovery detect a frame torn by a crash mid-append and truncate the file at the last valid
 * frame boundary instead of misinterpreting whatever partial bytes follow it — the same technique
 * write-ahead logs universally use.
 * <p>
 * {@code body} itself (issue #80) is {@code [1B codec version][bytes produced by that version's
 * InboxFrameCodec]} — RECORD and TOMBSTONE bodies alike. Writes always use {@link #CURRENT_CODEC};
 * reads dispatch on the version byte via {@link #resolveCodec(int)}, so the on-disk record/
 * tombstone layout can evolve later (a new field, a different payload encoding) by adding a new
 * {@link InboxFrameCodec} implementation without losing the ability to read what's already there.
 * <p>
 * <strong>Concurrency</strong>: every mutating/scanning operation is guarded by a single
 * intra-JVM lock on this instance — adequate for the message-level (not sub-microsecond) append
 * rate a single source's inbox sees. <strong>Not safe for two processes to share the same
 * directory</strong> (no OS-level file locking here, unlike {@code LockedFileStore}) — matches
 * the local file-based default's role: correctness for a single instance, with a Redis-backed
 * implementation (issue #72) as the answer for genuine multi-instance coordination.
 * <p>
 * <strong>Recovery</strong> ({@link #pendingEntries()}, and lazily before the first {@link
 * #enqueue}/{@link #acknowledge} call too) replays every segment file with this instance's own
 * prefix, in filename order — segment files are named with a fixed-width, zero-padded sequence
 * number so lexicographic and numeric order coincide — rebuilding the pending-entry index and
 * truncating a torn tail if the most recently written segment ends mid-frame.
 * <p>
 * <strong>Retention</strong>: a segment is deleted once every entry it ever recorded has been
 * acknowledged <em>and</em> it is not the currently open segment — checked synchronously right
 * after each acknowledgment, no background compaction thread. Deliberately simpler than
 * Kafka-grade log compaction; nothing here has needed more than this yet.
 */
public final class SegmentedLogDurableInbox implements DurableInbox {

    /**
     * Unvalidated by benchmark yet (see issue #70's own design discussion) — a deliberate,
     * "generous but finite" starting point in the same spirit as this codebase's other defaults
     * (e.g. {@code FlowExecutionDumpInMemoryRepository.DEFAULT_MAX_SIZE}), correctable later
     * without any format/design change.
     */
    public static final long DEFAULT_MAX_SEGMENT_SIZE_BYTES = 64L * 1024 * 1024;

    private static final String SEGMENT_FILE_EXTENSION = ".inbox";
    private static final int SEGMENT_NAME_DIGITS = 20;

    private static final byte FRAME_TYPE_RECORD = 1;
    private static final byte FRAME_TYPE_TOMBSTONE = 2;

    private static final InboxFrameCodec CURRENT_CODEC = InboxFrameCodecV1.INSTANCE;
    private static final Map<Integer, InboxFrameCodec> KNOWN_CODECS = Map.of(
        InboxFrameCodecV1.INSTANCE.version(), InboxFrameCodecV1.INSTANCE
    );

    private final Path directory;
    private final String namePrefix;
    private final IdentityGenerator identityGenerator;
    private final long maxSegmentSizeBytes;
    private final boolean fsyncEveryAppend;

    private final Object lock = new Object();

    // All fields below are guarded by `lock` and only meaningful once `initialized` is true.
    private boolean initialized = false;
    private final Map<String, SegmentedLogInboxEntry> pendingById = new LinkedHashMap<>();
    private final Map<String, Long> segmentByEntryId = new LinkedHashMap<>();
    private final Map<Long, Integer> pendingCountBySegment = new LinkedHashMap<>();
    private long currentSegmentSeq;
    private RandomAccessFile currentSegmentFile;
    private long currentSegmentSize;

    public SegmentedLogDurableInbox(Path directory, String namePrefix, IdentityGenerator identityGenerator) {
        this(directory, namePrefix, identityGenerator, DEFAULT_MAX_SEGMENT_SIZE_BYTES, true);
    }

    public SegmentedLogDurableInbox(Path directory, String namePrefix, IdentityGenerator identityGenerator,
                                     long maxSegmentSizeBytes, boolean fsyncEveryAppend) {
        this.directory = Objects.requireNonNull(directory, "directory is required and cannot be null");
        this.namePrefix = Objects.requireNonNull(namePrefix, "namePrefix is required and cannot be null");
        this.identityGenerator = Objects.requireNonNull(identityGenerator, "identityGenerator is required and cannot be null");
        if (maxSegmentSizeBytes <= 0) {
            throw new IllegalArgumentException("maxSegmentSizeBytes must be a positive integer, got " + maxSegmentSizeBytes);
        }
        this.maxSegmentSizeBytes = maxSegmentSizeBytes;
        this.fsyncEveryAppend = fsyncEveryAppend;
    }

    @Override
    public String enqueue(byte[] payload, Map<String, String> metadata) {
        Objects.requireNonNull(payload, "payload is required and cannot be null");
        synchronized (lock) {
            ensureInitialized();
            final String id = identityGenerator.nextIdAsText();
            final long segmentSeq = appendFrame(FRAME_TYPE_RECORD,
                withCodecVersion(CURRENT_CODEC.encodeRecord(id, metadata, payload)));
            pendingById.put(id, new SegmentedLogInboxEntry(id, payload, metadata));
            segmentByEntryId.put(id, segmentSeq);
            pendingCountBySegment.merge(segmentSeq, 1, Integer::sum);
            return id;
        }
    }

    @Override
    public List<InboxEntry> pendingEntries() {
        synchronized (lock) {
            ensureInitialized();
            return new ArrayList<>(pendingById.values());
        }
    }

    @Override
    public void acknowledge(String entryId) {
        Objects.requireNonNull(entryId, "entryId is required and cannot be null");
        synchronized (lock) {
            ensureInitialized();
            if (!pendingById.containsKey(entryId)) {
                // Already acknowledged (or never known to this instance) - acknowledge is
                // explicitly safe to call more than once, see this class's own interface Javadoc.
                return;
            }
            appendFrame(FRAME_TYPE_TOMBSTONE, withCodecVersion(CURRENT_CODEC.encodeTombstone(entryId)));
            pendingById.remove(entryId);
            final Long segmentSeq = segmentByEntryId.remove(entryId);
            if (segmentSeq != null) {
                final int remaining = pendingCountBySegment.merge(segmentSeq, -1, Integer::sum);
                if (remaining <= 0 && segmentSeq != currentSegmentSeq) {
                    pendingCountBySegment.remove(segmentSeq);
                    deleteSegmentFile(segmentSeq);
                }
            }
        }
    }

    // --- initialization / recovery ---------------------------------------------------------

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to create directory '%s'", directory), exception);
        }
        final List<Long> segmentSeqs = listSegmentSeqsSorted();
        if (segmentSeqs.isEmpty()) {
            currentSegmentSeq = 0;
            openCurrentSegmentForAppend();
        } else {
            for (long seq : segmentSeqs) {
                replaySegment(seq);
            }
            currentSegmentSeq = segmentSeqs.get(segmentSeqs.size() - 1);
            openCurrentSegmentForAppend();
            compactFullyAcknowledgedSegments(segmentSeqs);
        }
        initialized = true;
    }

    private void compactFullyAcknowledgedSegments(List<Long> segmentSeqs) {
        for (long seq : segmentSeqs) {
            if (seq != currentSegmentSeq && pendingCountBySegment.getOrDefault(seq, 0) <= 0) {
                pendingCountBySegment.remove(seq);
                deleteSegmentFile(seq);
            }
        }
    }

    /**
     * Filters {@code directory}'s listing down to this instance's own segments — the directory is
     * shared by every resource's {@link SegmentedLogDurableInbox} (see this class's own Javadoc),
     * so a plain extension filter isn't enough; only files named {@code <namePrefix>_<seq>.inbox}
     * belong to this instance.
     */
    private List<Long> listSegmentSeqsSorted() {
        final String filePrefix = namePrefix + "_";
        try (Stream<Path> files = Files.list(directory)) {
            return files
                .map(path -> path.getFileName().toString())
                .filter(name -> name.startsWith(filePrefix) && name.endsWith(SEGMENT_FILE_EXTENSION))
                .map(name -> Long.parseLong(name.substring(filePrefix.length(), name.length() - SEGMENT_FILE_EXTENSION.length())))
                .sorted()
                .collect(java.util.stream.Collectors.toList());
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to list directory '%s'", directory), exception);
        }
    }

    /**
     * Replays a single segment file, applying every valid frame to the in-memory index. Also
     * truncates the file if it ends with a frame torn by a crash mid-append (detected via a
     * short read or a CRC mismatch) — safe to apply to every segment, not just the most recently
     * written one: an already-closed, well-formed segment simply never triggers the truncation
     * branch.
     */
    private void replaySegment(long seq) {
        final Path file = segmentPath(seq);
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            long validLength = 0;
            while (true) {
                if (raf.length() - raf.getFilePointer() < 4) {
                    break;
                }
                final int frameLength;
                try {
                    frameLength = raf.readInt();
                } catch (EOFException eof) {
                    break;
                }
                if (frameLength < 1 || raf.length() - raf.getFilePointer() < (long) frameLength + 4) {
                    break;
                }
                final byte[] typeAndBody = new byte[frameLength];
                raf.readFully(typeAndBody);
                final int storedCrc = raf.readInt();
                if ((int) crc32Of(typeAndBody) != storedCrc) {
                    break;
                }
                validLength = raf.getFilePointer();
                applyFrame(seq, typeAndBody);
            }
            if (validLength < raf.length()) {
                raf.setLength(validLength);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to replay segment '%s'", file), exception);
        }
    }

    private void applyFrame(long segmentSeq, byte[] typeAndBody) {
        final byte type = typeAndBody[0];
        final byte[] body = java.util.Arrays.copyOfRange(typeAndBody, 1, typeAndBody.length);
        final InboxFrameCodec codec = resolveCodec(Byte.toUnsignedInt(body[0]));
        final byte[] codecBody = java.util.Arrays.copyOfRange(body, 1, body.length);
        if (type == FRAME_TYPE_RECORD) {
            final DecodedRecord record = codec.decodeRecord(codecBody);
            pendingById.put(record.id(), new SegmentedLogInboxEntry(record.id(), record.payload(), record.metadata()));
            segmentByEntryId.put(record.id(), segmentSeq);
            pendingCountBySegment.merge(segmentSeq, 1, Integer::sum);
        } else if (type == FRAME_TYPE_TOMBSTONE) {
            final String id = codec.decodeTombstone(codecBody);
            pendingById.remove(id);
            final Long originalSegment = segmentByEntryId.remove(id);
            if (originalSegment != null) {
                pendingCountBySegment.merge(originalSegment, -1, Integer::sum);
            }
        } else {
            throw new IllegalStateException(String.format("Unrecognized inbox frame type %d in segment %d", type, segmentSeq));
        }
    }

    // --- segment file management -------------------------------------------------------------

    private Path segmentPath(long seq) {
        return directory.resolve(String.format("%s_%0" + SEGMENT_NAME_DIGITS + "d%s", namePrefix, seq, SEGMENT_FILE_EXTENSION));
    }

    private void openCurrentSegmentForAppend() {
        try {
            currentSegmentFile = new RandomAccessFile(segmentPath(currentSegmentSeq).toFile(), "rw");
            currentSegmentFile.seek(currentSegmentFile.length());
            currentSegmentSize = currentSegmentFile.length();
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to open segment %d for append", currentSegmentSeq), exception);
        }
    }

    private void rollToNewSegment() {
        try {
            currentSegmentFile.close();
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to close segment %d before rollover", currentSegmentSeq), exception);
        }
        currentSegmentSeq++;
        openCurrentSegmentForAppend();
    }

    private void deleteSegmentFile(long seq) {
        try {
            Files.deleteIfExists(segmentPath(seq));
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to delete fully-acknowledged segment %d", seq), exception);
        }
    }

    // --- frame I/O -----------------------------------------------------------------------------

    private long appendFrame(byte type, byte[] body) {
        if (currentSegmentSize >= maxSegmentSizeBytes) {
            rollToNewSegment();
        }
        final byte[] typeAndBody = new byte[1 + body.length];
        typeAndBody[0] = type;
        System.arraycopy(body, 0, typeAndBody, 1, body.length);
        final int crc = (int) crc32Of(typeAndBody);
        try {
            currentSegmentFile.writeInt(typeAndBody.length);
            currentSegmentFile.write(typeAndBody);
            currentSegmentFile.writeInt(crc);
            if (fsyncEveryAppend) {
                currentSegmentFile.getFD().sync();
            }
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to append to segment %d", currentSegmentSeq), exception);
        }
        currentSegmentSize += 4 + typeAndBody.length + 4;
        return currentSegmentSeq;
    }

    private static long crc32Of(byte[] data) {
        final CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    // --- record/tombstone body versioning (issue #80) -------------------------------------------
    // The body layout itself (id/metadata/payload for a RECORD, just the id for a TOMBSTONE) is
    // owned by InboxFrameCodec, one implementation per format version - this class only owns
    // prefixing/stripping the version byte uniformly for both frame types.

    private static byte[] withCodecVersion(byte[] codecBody) {
        final byte[] versioned = new byte[1 + codecBody.length];
        versioned[0] = (byte) CURRENT_CODEC.version();
        System.arraycopy(codecBody, 0, versioned, 1, codecBody.length);
        return versioned;
    }

    private static InboxFrameCodec resolveCodec(int version) {
        final InboxFrameCodec codec = KNOWN_CODECS.get(version);
        if (codec == null) {
            throw new IllegalStateException(String.format("Unrecognized inbox frame codec version %d", version));
        }
        return codec;
    }

}
