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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
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
 * one per file name, then an OS-level {@link FileChannel#lock()} for the duration the file is
 * open. Both are required — {@code FileChannel} locks are held on behalf of the entire process,
 * so a second thread in the <em>same</em> JVM attempting to lock a region already locked by this
 * JVM does not block and wait, it fails immediately with {@link java.nio.channels.OverlappingFileLockException}.
 * The in-JVM lock serializes same-process threads before any of them reach the OS lock; the OS
 * lock is what then also serializes access from other processes writing under the same directory.
 */
public class LockedFileStore {

    private final Path directory;
    private final ConcurrentHashMap<String, ReentrantLock> intraJvmLocks = new ConcurrentHashMap<>();

    public LockedFileStore(Path directory) {
        this.directory = Preconditions.notNull(directory, "directory is required and cannot be null");
    }

    public Optional<String> readLocked(String fileName) {
        return withLocks(fileName, file -> {
            try (FileChannel fc = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
                 FileLock lock = fc.lock()) {
                if (fc.size() == 0) {
                    return Optional.empty();
                }
                return Optional.of(readAsText(fc));
            } catch (IOException exception) {
                throw new IllegalStateException(String.format("Unable to read locked file '%s'", file), exception);
            }
        });
    }

    public void writeLocked(String fileName, String content) {
        withLocks(fileName, file -> {
            try (FileChannel fc = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
                 FileLock lock = fc.lock()) {
                fc.truncate(0);
                fc.position(0);
                fc.write(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
                return null;
            } catch (IOException exception) {
                throw new IllegalStateException(String.format("Unable to write locked file '%s'", file), exception);
            }
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
            try (FileChannel fc = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
                 FileLock lock = fc.lock()) {
                final Optional<String> current = fc.size() == 0 ? Optional.empty() : Optional.of(readAsText(fc));
                final String updated = transform.apply(current);
                fc.truncate(0);
                fc.position(0);
                fc.write(ByteBuffer.wrap(updated.getBytes(StandardCharsets.UTF_8)));
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

    private static String readAsText(FileChannel fc) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate((int) fc.size());
        fc.position(0);
        while (buffer.hasRemaining() && fc.read(buffer) != -1) {
            // keep reading until the buffer is full
        }
        return new String(buffer.array(), StandardCharsets.UTF_8);
    }

}
