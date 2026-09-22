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
package io.pipelite.common.support.fs;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * A claim on a file, held open for as long as the caller keeps this object around - unlike {@link
 * LockedFileStore}, whose every method opens, does one operation, and releases before returning.
 * Built for issue #76: a caller that needs to know, unambiguously and without guessing a timeout,
 * whether an earlier claim is still genuinely held or the process that took it is simply gone.
 * <p>
 * The OS releases a {@link FileLock} immediately and unconditionally when the process holding it
 * terminates, gracefully or by a hard crash - no lease, no expiry, no second claimant risking a
 * concurrent duplicate attempt against work that is merely slow rather than abandoned. {@link
 * #tryAcquire(Path)} is non-blocking: it fails, rather than waits, if the lock is already held -
 * by another process sharing the same file, or by this same JVM (any earlier, still-open {@code
 * ProcessScopedFileLock} on the same path, since an OS file lock is scoped to the whole process,
 * not to the channel that acquired it).
 * <p>
 * What this does not do by itself: distinguish "the process is gone" from "the process is alive
 * but the one thread that held this lock died without ever closing it" - a live process's lock
 * cannot be broken by anyone, itself included, from the outside. A caller that needs to recover
 * from that narrower case is responsible for its own self-cleanup, using {@link
 * #heldLongerThan(Duration)} against claims *it* opened - see {@code
 * FileFlowExecutionDumpRepository}'s own sweep for the shape of that.
 */
public final class ProcessScopedFileLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;
    private final Instant acquiredAt;

    private ProcessScopedFileLock(FileChannel channel, FileLock lock, Instant acquiredAt) {
        this.channel = channel;
        this.lock = lock;
        this.acquiredAt = acquiredAt;
    }

    /**
     * @return the lock, if it was free; empty if it is already held - by another process, or by
     * this one - without waiting.
     */
    public static Optional<ProcessScopedFileLock> tryAcquire(Path file) {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to create directory '%s'", file.getParent()), exception);
        }
        FileChannel channel = null;
        try {
            channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
            final FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException alreadyHeldInThisJvm) {
                // FileLock is scoped to the whole process, not the channel that acquired it: a
                // second, independent open()+tryLock() on a path this same JVM already holds
                // throws here rather than returning null - both mean the same thing to this caller.
                channel.close();
                return Optional.empty();
            }
            if (lock == null) {
                // Held by another process: tryLock() returns null rather than throwing.
                channel.close();
                return Optional.empty();
            }
            return Optional.of(new ProcessScopedFileLock(channel, lock, Instant.now()));
        } catch (IOException exception) {
            closeQuietly(channel);
            throw new IllegalStateException(String.format("Unable to acquire a process-scoped lock on '%s'", file), exception);
        }
    }

    /**
     * Whether this lock has been held, by this same object, for longer than {@code duration} -
     * the one thing a caller can legitimately decide about its own claim without any external
     * coordination (see this class's own Javadoc on why it can never decide this about someone
     * else's).
     */
    public boolean heldLongerThan(Duration duration) {
        return Duration.between(acquiredAt, Instant.now()).compareTo(duration) > 0;
    }

    /**
     * Releases the lock and closes the underlying channel. Safe to call more than once; the
     * second call is a no-op beyond redundant, already-idempotent {@code release()}/{@code
     * close()} calls on the underlying JDK objects.
     */
    @Override
    public void close() {
        try {
            lock.release();
        } catch (IOException ignored) {
            // Best-effort: closing the channel just below releases the OS-level lock regardless.
        } finally {
            closeQuietly(channel);
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
        }
    }

}
