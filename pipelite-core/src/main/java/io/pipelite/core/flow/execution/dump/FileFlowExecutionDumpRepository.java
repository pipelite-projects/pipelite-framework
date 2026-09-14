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
package io.pipelite.core.flow.execution.dump;

import io.pipelite.common.support.Preconditions;
import io.pipelite.common.support.fs.LockedFileStore;
import io.pipelite.common.support.fs.PipeliteHome;
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.FlowExecutionDumpStatus;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * File-backed {@link FlowExecutionDumpRepository}: one {@code .dump} file per dump under a
 * caller-supplied directory ({@code DefaultPipeliteContext}'s default is {@code
 * PipeliteHome.resolve("state/flow-execution-dumps")} — see issue #68), with locked reads/writes
 * delegated to {@link LockedFileStore}. Unlike {@link FlowExecutionDumpInMemoryRepository} (no
 * longer the default, kept for callers that want zero I/O over durability), a saved dump survives
 * a process crash or restart.
 * <p>
 * {@link #poll()} keeps no secondary ordering index — deliberately, mirroring the
 * file-channel-adapter's own {@code FileTailStateStore}, which keeps its index "debug only, not
 * read by runtime" to avoid an index drifting out of sync with the files it describes. Instead it
 * lists the directory and reads/parses every pending dump's full content (not just its creation
 * time) to find the oldest one — more expensive than the in-memory repository's own O(n) scan,
 * which only compares already-deserialized objects already sitting in heap, and worst exactly
 * when retries pile up after a downstream outage. Accepted given the same "generous but finite"
 * pending-dump volume the in-memory repository's own capacity cap already assumes, and the
 * retry-channel's default one-poll-per-second cadence ({@code ScheduledPollingConsumerService}),
 * not a tight loop.
 * <p>
 * {@link #poll()} itself is not exclusive — nothing stops two callers, in this JVM or another
 * process sharing this directory, from both being handed the same dump if both call {@code poll()}
 * before either has claimed it. What actually excludes a dump from being handed out twice is its
 * own {@link io.pipelite.core.flow.execution.FlowExecutionDumpStatus}, set via {@link
 * #tryClaim(String)} — a single {@link LockedFileStore#readAndWriteLocked} read-check-write, whose
 * {@code FileChannel} lock is held for the whole operation and enforced by the OS across processes,
 * not just this JVM. A caller must always follow up a {@code poll()} with a {@code tryClaim(id)}
 * before acting on what it returned, and treat a {@code false} result as "someone else already has
 * this one" rather than a plain no-op. A crash between a successful claim and the eventual {@link
 * #remove(String)} leaves that dump durably {@code IN_PROGRESS} and therefore unreachable via
 * {@code poll()} forever — no lease/expiry exists yet to reclaim it automatically; tracked as a
 * follow-up, not solved here. It is a different, more serious problem for two genuinely
 * <em>different</em> applications to collide on this directory at all (one application's
 * retry-channel attempting to resolve a dump whose {@code flowName} only exists in the other's flow
 * registry) — see {@link PipeliteHome}'s {@code pipelite.application.id} for how to namespace two
 * such applications apart.
 */
public class FileFlowExecutionDumpRepository implements FlowExecutionDumpRepository {

    private static final String FILE_EXTENSION = ".dump";

    private static final String ID_KEY = "id";
    private static final String CREATION_TIME_KEY = "creationTime";
    private static final String FLOW_HASH_KEY = "flowHash";
    private static final String FLOW_NAME_KEY = "flowName";
    private static final String SOURCE_ENDPOINT_RESOURCE_KEY = "sourceEndpointResource";
    private static final String LAST_EXECUTED_PROCESSOR_KEY = "lastExecutedProcessor";
    private static final String FAILED_PROCESSOR_KEY = "failedProcessor";
    private static final String ATTEMPT_NUMBER_KEY = "attemptNumber";
    private static final String STACK_TRACE_KEY = "stackTrace";
    private static final String MAX_ATTEMPTS_KEY = "maxAttempts";
    private static final String DEAD_LETTER_FLOW_NAME_KEY = "deadLetterFlowName";
    private static final String EXCHANGE_DATA_KEY = "exchangeData";
    private static final String ENCODING_KEY = "encoding";
    private static final String STATUS_KEY = "status";

    private final Path directory;
    private final LockedFileStore store;

    public FileFlowExecutionDumpRepository(Path directory) {
        this.directory = Preconditions.notNull(directory, "directory is required and cannot be null");
        this.store = new LockedFileStore(directory);
    }

    @Override
    public Optional<FlowExecutionDump> tryLoad(String id) {
        return store.readLocked(fileName(id)).map(FileFlowExecutionDumpRepository::parse);
    }

    @Override
    public Optional<FlowExecutionDump> poll() {
        // Avoids Files.list(...) throwing on a directory LockedFileStore hasn't created yet
        // (nothing has ever been saved) - same lazy-creation contract LockedFileStore documents.
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files
                .filter(path -> path.getFileName().toString().endsWith(FILE_EXTENSION))
                .map(path -> store.readLocked(path.getFileName().toString()))
                .flatMap(Optional::stream)
                .map(FileFlowExecutionDumpRepository::parse)
                .filter(dump -> dump.getStatus() == FlowExecutionDumpStatus.PENDING)
                .min(Comparator.comparing(FlowExecutionDump::getCreationTime));
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to list directory '%s'", directory), exception);
        }
    }

    @Override
    public void save(FlowExecutionDump flowExecutionDump) {
        if (!(flowExecutionDump instanceof SerializedFlowExecutionDump)) {
            // Not a hypothetical: FlowExecutionDumpFactory (the only production caller of save())
            // never builds anything else, and DefaultFlowExecutionDump (the sibling implementation
            // holding a live, non-serializable Exchange reference) is never actually instantiated
            // anywhere in this codebase - but this repository, unlike the in-memory one, genuinely
            // cannot persist a dump without exchangeData/encoding, which only this type exposes.
            throw new IllegalArgumentException(String.format(
                "%s can only persist %s instances (got %s)",
                getClass().getSimpleName(), SerializedFlowExecutionDump.class.getSimpleName(),
                flowExecutionDump.getClass().getName()));
        }
        store.writeLocked(fileName(flowExecutionDump.getId()), format((SerializedFlowExecutionDump) flowExecutionDump));
    }

    @Override
    public void remove(String id) {
        store.deleteLocked(fileName(id));
    }

    @Override
    public boolean tryClaim(String id) {
        final AtomicBoolean claimed = new AtomicBoolean(false);
        // readAndWriteLocked holds one FileLock (in-JVM + OS-level, see LockedFileStore's own
        // Javadoc) across the whole read-check-write, so two callers racing to claim the same id -
        // whether two threads in this JVM or two separate process instances sharing this directory
        // - can't both observe PENDING and both win, the same guarantee tryClaim's own contract
        // requires.
        store.readAndWriteLocked(fileName(id), current -> {
            if (!current.isPresent()) {
                // Nothing to claim - already resolved and removed by whoever's attempt finished
                // first, or never existed. readAndWriteLocked requires a String back regardless;
                // "" round-trips as still-absent (parse() is never called on it since poll()/
                // tryLoad() both go through readLocked, which already treats an empty file as
                // absent - same convention this repository uses everywhere else).
                return "";
            }
            final FlowExecutionDump dump = parse(current.get());
            if (dump.getStatus() != FlowExecutionDumpStatus.PENDING) {
                return current.get();
            }
            dump.setStatus(FlowExecutionDumpStatus.IN_PROGRESS);
            claimed.set(true);
            return format((SerializedFlowExecutionDump) dump);
        });
        return claimed.get();
    }

    private static String fileName(String id) {
        return id + FILE_EXTENSION;
    }

    private static String format(SerializedFlowExecutionDump dump) {

        final Properties properties = new Properties();
        properties.setProperty(ID_KEY, dump.getId());
        properties.setProperty(CREATION_TIME_KEY, dump.getCreationTime().toString());
        properties.setProperty(FLOW_HASH_KEY, dump.getFlowHash());
        properties.setProperty(FLOW_NAME_KEY, dump.getFlowName());
        putIfNotNull(properties, SOURCE_ENDPOINT_RESOURCE_KEY, dump.getSourceEndpointResource());
        putIfNotNull(properties, LAST_EXECUTED_PROCESSOR_KEY, dump.getLastExecutedProcessor());
        putIfNotNull(properties, FAILED_PROCESSOR_KEY, dump.getFailedProcessor());
        properties.setProperty(ATTEMPT_NUMBER_KEY, String.valueOf(dump.getAttemptNumber()));
        putIfNotNull(properties, STACK_TRACE_KEY, dump.getStackTrace());
        properties.setProperty(MAX_ATTEMPTS_KEY, String.valueOf(dump.getMaxAttempts()));
        putIfNotNull(properties, DEAD_LETTER_FLOW_NAME_KEY, dump.getDeadLetterFlowName());
        putIfNotNull(properties, EXCHANGE_DATA_KEY, dump.getExchangeData());
        putIfNotNull(properties, ENCODING_KEY, dump.getEncoding());
        properties.setProperty(STATUS_KEY, dump.getStatus().name());

        final StringWriter writer = new StringWriter();
        try {
            // No comments: Properties#store still appends its own "#<current date>" line
            // regardless of what's passed here, so the file is never byte-identical across two
            // writes of the same dump anyway - assert on parsed fields in tests, never on raw
            // file content.
            properties.store(writer, null);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to format flow execution dump", exception);
        }
        return writer.toString();
    }

    private static void putIfNotNull(Properties properties, String key, String value) {
        // Properties#setProperty throws NPE on a null value, but deadLetterFlowName and
        // failedProcessor are legitimately null in the common case (no dead-letter configured;
        // failure occurred outside a processor's own catch block) and null is semantically
        // distinct from "" for both - so a missing key (Properties#getProperty already returns
        // null for one) round-trips correctly, applied uniformly rather than special-cased.
        if (value != null) {
            properties.setProperty(key, value);
        }
    }

    private static FlowExecutionDump parse(String content) {

        final Properties properties = new Properties();
        try {
            properties.load(new StringReader(content));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse flow execution dump", exception);
        }

        final SerializedFlowExecutionDump dump = new SerializedFlowExecutionDump(
            properties.getProperty(ID_KEY),
            properties.getProperty(FLOW_HASH_KEY),
            properties.getProperty(FLOW_NAME_KEY),
            LocalDateTime.parse(properties.getProperty(CREATION_TIME_KEY)));

        dump.setSourceEndpointResource(properties.getProperty(SOURCE_ENDPOINT_RESOURCE_KEY));
        dump.setLastExecutedProcessor(properties.getProperty(LAST_EXECUTED_PROCESSOR_KEY));
        dump.setFailedProcessor(properties.getProperty(FAILED_PROCESSOR_KEY));
        dump.setAttemptNumber(Integer.parseInt(properties.getProperty(ATTEMPT_NUMBER_KEY)));
        dump.setStackTrace(properties.getProperty(STACK_TRACE_KEY));
        dump.setMaxAttempts(Integer.parseInt(properties.getProperty(MAX_ATTEMPTS_KEY)));
        dump.setDeadLetterFlowName(properties.getProperty(DEAD_LETTER_FLOW_NAME_KEY));
        dump.setExchangeData(properties.getProperty(EXCHANGE_DATA_KEY), properties.getProperty(ENCODING_KEY));
        // Defaults to PENDING for a file written before this field existed - never actually
        // happens today (nothing has shipped yet), kept as a safe default rather than a hard
        // parse failure on an unrecognized/missing value.
        final String statusText = properties.getProperty(STATUS_KEY);
        dump.setStatus(statusText != null ? FlowExecutionDumpStatus.valueOf(statusText) : FlowExecutionDumpStatus.PENDING);

        return dump;
    }

}
