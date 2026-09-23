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

import io.pipelite.common.support.Preconditions;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Provides safe, locked I/O access to files within a single directory. A file is addressed
 * directly by its name — this class has no notion of logical keys, hashing, or value
 * serialization; deriving a file name from domain data and parsing/formatting its content are
 * the caller's responsibility.
 * <p>
 * The target directory is created lazily, on first {@link #readLocked(String)},
 * {@link #writeLocked(String, String)}, or {@link #readAndWriteLocked(String, Function)} call,
 * not at construction time.
 * <p>
 * Each access to a given file name is guarded by two layers: an in-JVM {@link ReentrantLock},
 * one per file name, then an OS-level {@link FileChannel#lock()} held for the whole operation.
 * Both are required — {@code FileChannel} locks are held on behalf of the entire process, so a
 * second thread in the <em>same</em> JVM attempting to lock a region already locked by this JVM
 * does not block and wait, it fails immediately with {@link java.nio.channels.OverlappingFileLockException}.
 * The in-JVM lock serializes same-process threads before any of them reach the OS lock; the OS
 * lock is what then also serializes access from other processes writing under the same directory.
 * <p>
 * The OS lock is taken on a dedicated {@code <fileName>.lock} file, never on the data file itself
 * (issue #123): a data file is only ever fully replaced via a temp file plus an atomic {@link
 * Files#move}, so a process killed mid-write leaves the previous content (or nothing, if the file
 * was new) untouched rather than a truncated fragment. That rename requires the data file to have
 * no open handle on it at all - confirmed empirically that Windows refuses to replace a file with
 * <em>any</em> open handle, locked or not, unlike POSIX - which is precisely why the lock cannot
 * live on the data file the way it used to. The {@code .lock} file itself is never renamed, so it
 * is always safe to hold a lock on; it lingers harmlessly after a data file is deleted (the next
 * caller for that file name simply reopens it) except where a repository explicitly cleans it up
 * (see e.g. {@code FileFlowExecutionDumpRepository}'s own {@code .claim} file convention).
 * <p>
 * This only protects callers that go through this class - confirmed empirically that on Windows,
 * anything else with the data file open at the moment of the rename above (a text editor, an
 * antivirus scan, a backup tool, or a test that reads the file directly instead of through {@link
 * #readLocked(String)}) can make {@code writeLocked}/{@code readAndWriteLocked} fail with an
 * {@code AccessDeniedException}, even with no lock of its own - Windows refuses the rename
 * regardless of whether the open handle is locked. There is no code-level fix for that: it is a
 * platform characteristic of replacing a file that something outside this class's own locking
 * protocol is touching directly, not a defect in the rename itself.
 */
public class LockedFileStore {

    private static final String LOCK_FILE_EXTENSION = ".lock";
    private static final String TEMP_FILE_EXTENSION = ".tmp";

    private final Path directory;
    private final ConcurrentHashMap<String, ReentrantLock> intraJvmLocks = new ConcurrentHashMap<>();

    public LockedFileStore(Path directory) {
        this.directory = Preconditions.notNull(directory, "directory is required and cannot be null");
    }

    public Optional<String> readLocked(String fileName) {
        return withLocks(fileName, file -> {
            try (FileChannel lockChannel = openLockChannel(fileName);
                 FileLock lock = lockChannel.lock()) {
                return readCurrent(file);
            } catch (IOException exception) {
                throw new IllegalStateException(String.format("Unable to read locked file '%s'", file), exception);
            }
        });
    }

    public void writeLocked(String fileName, String content) {
        withLocks(fileName, file -> {
            try (FileChannel lockChannel = openLockChannel(fileName);
                 FileLock lock = lockChannel.lock()) {
                writeAtomically(file, content);
                return null;
            } catch (IOException exception) {
                throw new IllegalStateException(String.format("Unable to write locked file '%s'", file), exception);
            }
        });
    }

    /**
     * Deletes {@code fileName} if it exists, under the same two-layer locking as every other
     * method here — a plain {@code Files.deleteIfExists} would bypass both the in-JVM lock and
     * the OS-level lock this class exists to provide, letting it race a concurrent
     * {@link #readLocked(String)}/{@link #writeLocked(String, String)} on the same file name from
     * another thread in this JVM. Also removes this file name's own {@code .lock} file, best
     * effort, once the data-file deletion's own lock is released - a lingering one is harmless
     * (the next caller just recreates it), so a failure here is logged nowhere and simply ignored.
     */
    public void deleteLocked(String fileName) {
        withLocks(fileName, file -> {
            try (FileChannel lockChannel = openLockChannel(fileName);
                 FileLock lock = lockChannel.lock()) {
                Files.deleteIfExists(file);
            } catch (IOException exception) {
                throw new IllegalStateException(String.format("Unable to delete locked file '%s'", file), exception);
            }
            deleteLockFileQuietly(fileName);
            return null;
        });
    }

    /**
     * Atomically applies {@code transform} to the current content of {@code fileName} (empty if
     * the file is new or empty) and writes back what it returns — a single lock held for the
     * whole read-modify-write, unlike calling {@link #readLocked(String)} followed by
     * {@link #writeLocked(String, String)} separately.
     */
    public void readAndWriteLocked(String fileName, Function<Optional<String>, String> transform) {
        withLocks(fileName, file -> {
            try (FileChannel lockChannel = openLockChannel(fileName);
                 FileLock lock = lockChannel.lock()) {
                final Optional<String> current = readCurrent(file);
                final String updated = transform.apply(current);
                writeAtomically(file, updated);
                return null;
            } catch (IOException exception) {
                throw new IllegalStateException(String.format("Unable to read-and-write locked file '%s'", file), exception);
            }
        });
    }

    private <T> T withLocks(String fileName, Function<Path, T> operation) {
        ensureDirectory();
        final Path file = directory.resolve(fileName);
        final ReentrantLock intraJvmLock = intraJvmLocks.computeIfAbsent(fileName, key -> new ReentrantLock());
        intraJvmLock.lock();
        try {
            return operation.apply(file);
        } finally {
            intraJvmLock.unlock();
        }
    }

    private void ensureDirectory() {
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            throw new IllegalStateException(String.format("Unable to create directory '%s'", directory), exception);
        }
    }

    private FileChannel openLockChannel(String fileName) throws IOException {
        final Path lockFile = directory.resolve(fileName + LOCK_FILE_EXTENSION);
        return FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }

    private void deleteLockFileQuietly(String fileName) {
        try {
            Files.deleteIfExists(directory.resolve(fileName + LOCK_FILE_EXTENSION));
        } catch (IOException ignored) {
            // Best-effort: see this class's own Javadoc on why a lingering .lock file is harmless.
        }
    }

    private static Optional<String> readCurrent(Path file) throws IOException {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        final String content = Files.readString(file, StandardCharsets.UTF_8);
        return content.isEmpty() ? Optional.empty() : Optional.of(content);
    }

    /**
     * Writes {@code content} to {@code file} as a single atomic step (issue #123) - a temp file,
     * written in full, then renamed over {@code file}, never a truncate-then-write in place. See
     * this class's own Javadoc for why {@code file} must never itself be opened with a lock for
     * this rename to succeed on every platform.
     */
    private static void writeAtomically(Path file, String content) throws IOException {
        final Path tempFile = file.resolveSibling(file.getFileName().toString() + "." + UUID.randomUUID() + TEMP_FILE_EXTENSION);
        try {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.deleteIfExists(tempFile);
            throw exception;
        }
    }

}
