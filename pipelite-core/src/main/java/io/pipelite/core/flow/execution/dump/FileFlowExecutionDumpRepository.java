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
import io.pipelite.common.support.fs.ProcessScopedFileLock;
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.FlowExecutionDumpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * File-backed {@link FlowExecutionDumpRepository}: one {@code .dump} file per dump under a
 * caller-supplied directory ({@code DefaultPipeliteContext}'s default is {@code
 * PipeliteHome.resolve("state/retry")} — see issue #68), with locked reads/writes
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
 * before either has claimed it. What actually excludes a dump from being handed out twice is a
 * {@link ProcessScopedFileLock} on a dedicated {@code <id>.claim} file, acquired via {@link
 * #tryClaim(String)} and held open for the whole resume attempt, not the short-lived,
 * open-check-write-close pattern {@link LockedFileStore} otherwise uses everywhere in this class
 * (issue #76). A caller must always follow up a {@code poll()} with a {@code tryClaim(id)} before
 * acting on what it returned, and treat a {@code false} result as "someone else already has this
 * one" rather than a plain no-op.
 * <p>
 * A crash in the process that holds a claim releases its {@code FileLock} immediately - the OS
 * does this unconditionally, whatever killed the process - so {@code poll()}/{@code tryClaim} in
 * this process on restart, or in any other instance sharing this directory, can claim that same
 * dump again right away, with no waiting and no risk of a second, concurrent attempt against one
 * that is merely slow rather than gone (see {@link ProcessScopedFileLock}'s own Javadoc for why
 * that guarantee only holds for the process, not for one specific thread inside it dying without
 * closing its handle). The narrower "thread died, process didn't" case is what {@link
 * #sweepAbandonedClaims()} exists for: a process periodically checks its *own* open claims and
 * gives up on any held past {@link #safetyNetDuration} - never a guess about another process's,
 * or another thread's, still-genuine liveness. The dump's own {@link
 * io.pipelite.core.flow.execution.FlowExecutionDumpStatus} remains a cheap, fast filter for {@link
 * #poll()}, kept in sync with the lock but no longer what its exclusivity itself relies on.
 * <p>
 * It is a different, more serious problem for two genuinely <em>different</em> applications to
 * collide on this directory at all (one application's retry-channel attempting to resolve a dump
 * whose {@code flowName} only exists in the other's flow registry) — see {@link PipeliteHome}'s
 * {@code pipelite.application.id} for how to namespace two such applications apart.
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
    private static final String EXHAUSTION_ACTION_KEY = "exhaustionAction";
    private static final String DEAD_LETTER_TARGET_KEY = "deadLetterTarget";
    private static final String EXCHANGE_DATA_KEY = "exchangeData";
    private static final String ENCODING_KEY = "encoding";
    private static final String STATUS_KEY = "status";
    private static final String NEXT_ATTEMPT_TIME_KEY = "nextAttemptTime";

    private static final String CLAIM_FILE_EXTENSION = ".claim";

    /**
     * The self-cleanup safety net (issue #76): far above any legitimate resume attempt's expected
     * duration, since its only purpose is recovering the narrow "the thread that held this claim
     * died, but the process didn't" case - anything shorter risks mistaking a merely slow, still
     * genuinely running attempt for an abandoned one. A real crash is recovered immediately,
     * unconditionally, by the OS releasing the lock - this duration never delays that case.
     */
    public static final Duration DEFAULT_SAFETY_NET_DURATION = Duration.ofHours(1);

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final Path directory;
    private final LockedFileStore store;
    private final Duration safetyNetDuration;

    /**
     * One open {@link ProcessScopedFileLock} per {@code id} this process currently has claimed -
     * the actual source of truth for exclusivity (issue #76); the dump's own {@code status} field
     * remains a cheap, fast filter for {@link #poll()}, kept in sync with it but no longer what
     * exclusivity itself relies on. Swept on every {@link #poll()} call for entries this same
     * process has held past {@link #safetyNetDuration} - see this class's own Javadoc.
     */
    private final Map<String, ProcessScopedFileLock> openClaims = new ConcurrentHashMap<>();

    public FileFlowExecutionDumpRepository(Path directory) {
        this(directory, DEFAULT_SAFETY_NET_DURATION);
    }

    public FileFlowExecutionDumpRepository(Path directory, Duration safetyNetDuration) {
        this.directory = Preconditions.notNull(directory, "directory is required and cannot be null");
        this.safetyNetDuration = Preconditions.notNull(safetyNetDuration, "safetyNetDuration is required and cannot be null");
        this.store = new LockedFileStore(directory);
    }

    @Override
    public Optional<FlowExecutionDump> tryLoad(String id) {
        return store.readLocked(fileName(id)).map(FileFlowExecutionDumpRepository::parse);
    }

    @Override
    public Optional<FlowExecutionDump> poll() {
        // Piggybacked here (issue #76) rather than on tryClaim(id): poll() is what the retry
        // channel's scheduled loop calls unconditionally on every tick (RetryPollingConsumer#
        // receive()), backlog or not - the one call site that actually gives this a regular,
        // load-independent cadence to run on. tryClaim only ever runs when poll() already found
        // something, which would leave a sweep starved for however long the backlog stays empty.
        sweepAbandonedClaims();
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
                .filter(this::isEligible)
                .min(Comparator.comparing(FlowExecutionDump::getCreationTime));
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to list directory '%s'", directory), exception);
        }
    }

    /**
     * {@code PENDING} is eligible once due (issue #95: a dump with a future {@code
     * nextAttemptTime} is skipped, not offered, until the backoff it was captured with elapses).
     * An {@code IN_PROGRESS} dump this process did not itself just
     * abandon (that is {@link #sweepAbandonedClaims()}'s job, already run above, for this
     * process's own claims) is only eligible if whoever holds its claim is actually gone - which a
     * plain status flag can never tell on its own, since nothing updates it when a *different*
     * process crashes (issue #76: that process's own claim died with it, but its dump file is
     * untouched, forever {@code IN_PROGRESS}, unless something rediscovers that here). A
     * non-blocking probe on the claim file answers this directly: succeeding means nobody
     * currently holds the OS lock - the claiming process crashed - so the dump is reset to {@code
     * PENDING} and handed out again; failing means someone still genuinely holds it, whether
     * another process's live attempt, or this same JVM's own (an in-flight claim, or one not yet
     * swept - either way surfaces as {@link java.nio.channels.OverlappingFileLockException} inside
     * {@link ProcessScopedFileLock#tryAcquire}, not a successful probe).
     */
    private boolean isEligible(FlowExecutionDump dump) {
        if (dump.getStatus() == FlowExecutionDumpStatus.PENDING) {
            return dump.isDue();
        }
        final Optional<ProcessScopedFileLock> probe = ProcessScopedFileLock.tryAcquire(claimFile(dump.getId()));
        if (probe.isEmpty()) {
            return false;
        }
        probe.get().close();
        resetToPending(dump.getId());
        if (sysLogger.isWarnEnabled()) {
            sysLogger.warn("FlowExecutionDump {} was IN_PROGRESS with no live claim on it - " +
                    "the process that claimed it is gone; returning it to the pool",
                dump.getId());
        }
        return true;
    }

    private void resetToPending(String id) {
        store.readAndWriteLocked(fileName(id), current -> {
            if (current.isEmpty()) {
                return "";
            }
            final FlowExecutionDump dump = parse(current.get());
            dump.setStatus(FlowExecutionDumpStatus.PENDING);
            return format((SerializedFlowExecutionDump) dump);
        });
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
        // Release this process's own claim, if it holds one, before deleting either file: on
        // Windows a file with an open handle generally can't be deleted at all, and even where it
        // can (POSIX), leaving the lock file racing its own delete serves no purpose.
        releaseClaim(id);
        store.deleteLocked(fileName(id));
        deleteClaimFileQuietly(id);
    }

    @Override
    public boolean tryClaim(String id) {
        // The lock is what actually decides exclusivity (issue #76) - two callers racing to claim
        // the same id, whether two threads in this JVM or two separate process instances sharing
        // this directory, can't both acquire it, the same guarantee tryClaim's own contract
        // requires. Acquiring it is deliberately treated as sufficient on its own, whatever the
        // dump's own status currently says: tryClaim is the only path that ever sets IN_PROGRESS,
        // always paired with holding this exact lock, so an IN_PROGRESS dump whose lock is free to
        // acquire can only be one whose earlier claimant crashed before removing it (poll() runs
        // the same reasoning, as a probe, to decide whether to offer such a dump again at all - see
        // its own Javadoc; this is what actually commits to one once offered, including when
        // called without a prior poll(), which the interface's own contract does not require of a
        // caller in general even though today's one production caller always does).
        final Optional<ProcessScopedFileLock> lockHolder = ProcessScopedFileLock.tryAcquire(claimFile(id));
        if (lockHolder.isEmpty()) {
            return false;
        }
        final AtomicBoolean claimed = new AtomicBoolean(false);
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
            dump.setStatus(FlowExecutionDumpStatus.IN_PROGRESS);
            claimed.set(true);
            return format((SerializedFlowExecutionDump) dump);
        });
        if (claimed.get()) {
            openClaims.put(id, lockHolder.get());
        } else {
            lockHolder.get().close();
        }
        return claimed.get();
    }

    /**
     * This process giving up, on its own authority, on a claim it opened and has decided is
     * abandoned - never a guess about whether another process, or another thread of this one, is
     * still genuinely working on {@code id} (see {@link ProcessScopedFileLock}'s own Javadoc on
     * why that can never be decided from the outside). Run from {@link #poll()} on every call, so
     * it keeps a regular cadence regardless of backlog - see {@link #poll()}'s own comment.
     */
    private void sweepAbandonedClaims() {
        final Iterator<Map.Entry<String, ProcessScopedFileLock>> claims = openClaims.entrySet().iterator();
        while (claims.hasNext()) {
            final Map.Entry<String, ProcessScopedFileLock> claim = claims.next();
            if (!claim.getValue().heldLongerThan(safetyNetDuration)) {
                continue;
            }
            final String id = claim.getKey();
            claim.getValue().close();
            claims.remove();
            resetToPending(id);
            if (sysLogger.isWarnEnabled()) {
                sysLogger.warn("FlowExecutionDump {} was claimed more than {} ago and never removed - " +
                        "abandoning this process's own claim on it and returning it to the pool",
                    id, safetyNetDuration);
            }
        }
    }

    private void releaseClaim(String id) {
        final ProcessScopedFileLock lock = openClaims.remove(id);
        if (lock != null) {
            lock.close();
        }
    }

    private void deleteClaimFileQuietly(String id) {
        try {
            Files.deleteIfExists(claimFile(id));
        } catch (IOException exception) {
            if (sysLogger.isWarnEnabled()) {
                sysLogger.warn("Unable to delete claim file for FlowExecutionDump {}", id, exception);
            }
        }
    }

    private static String fileName(String id) {
        return id + FILE_EXTENSION;
    }

    private Path claimFile(String id) {
        return directory.resolve(id + CLAIM_FILE_EXTENSION);
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
        properties.setProperty(EXHAUSTION_ACTION_KEY, dump.getExhaustionAction().name());
        putIfNotNull(properties, DEAD_LETTER_TARGET_KEY, dump.getDeadLetterTarget());
        putIfNotNull(properties, EXCHANGE_DATA_KEY, dump.getExchangeData());
        putIfNotNull(properties, ENCODING_KEY, dump.getEncoding());
        properties.setProperty(STATUS_KEY, dump.getStatus().name());
        putIfNotNull(properties, NEXT_ATTEMPT_TIME_KEY,
            dump.getNextAttemptTime() != null ? dump.getNextAttemptTime().toString() : null);

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
        // Properties#setProperty throws NPE on a null value, but deadLetterTarget and
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
        // Defaults to NONE for a dump written before this field existed - same safe-default
        // convention as STATUS_KEY below (never actually happens today, pre-1.0.0).
        final String exhaustionActionText = properties.getProperty(EXHAUSTION_ACTION_KEY);
        dump.setExhaustionAction(exhaustionActionText != null
            ? FlowExecutionDump.ExhaustionAction.valueOf(exhaustionActionText)
            : FlowExecutionDump.ExhaustionAction.NONE);
        dump.setDeadLetterTarget(properties.getProperty(DEAD_LETTER_TARGET_KEY));
        dump.setExchangeData(properties.getProperty(EXCHANGE_DATA_KEY), properties.getProperty(ENCODING_KEY));
        // Defaults to PENDING for a file written before this field existed - never actually
        // happens today (nothing has shipped yet), kept as a safe default rather than a hard
        // parse failure on an unrecognized/missing value.
        final String statusText = properties.getProperty(STATUS_KEY);
        dump.setStatus(statusText != null ? FlowExecutionDumpStatus.valueOf(statusText) : FlowExecutionDumpStatus.PENDING);
        // Absent for a dump written before backoff existed, and for one captured with no
        // .backoff(...) declared - both mean "due immediately" (FlowExecutionDump#isDue()).
        final String nextAttemptTimeText = properties.getProperty(NEXT_ATTEMPT_TIME_KEY);
        dump.setNextAttemptTime(nextAttemptTimeText != null ? LocalDateTime.parse(nextAttemptTimeText) : null);

        return dump;
    }

}
