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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class LockedFileStoreTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path directory;
    private LockedFileStore subject;

    @Before
    public void setup() {
        directory = temporaryFolder.getRoot().toPath().resolve("store");
        subject = new LockedFileStore(directory);
    }

    @Test
    public void shouldReturnEmptyWhenFileDoesNotExist() {
        Assert.assertEquals(Optional.empty(), subject.readLocked("missing.txt"));
    }

    @Test
    public void shouldReturnEmptyWhenFileExistsButIsEmpty() {
        subject.writeLocked("empty.txt", "");
        Assert.assertEquals(Optional.empty(), subject.readLocked("empty.txt"));
    }

    @Test
    public void shouldRoundTripWriteThenRead() {
        subject.writeLocked("state.txt", "hello world");
        Assert.assertEquals(Optional.of("hello world"), subject.readLocked("state.txt"));
    }

    @Test
    public void shouldTruncatePreviousContentOnOverwrite() {
        subject.writeLocked("state.txt", "a much longer initial value");
        subject.writeLocked("state.txt", "short");
        Assert.assertEquals(Optional.of("short"), subject.readLocked("state.txt"));
    }

    @Test
    public void shouldKeepDifferentFileNamesIndependent() {
        subject.writeLocked("a.txt", "value-a");
        subject.writeLocked("b.txt", "value-b");
        Assert.assertEquals(Optional.of("value-a"), subject.readLocked("a.txt"));
        Assert.assertEquals(Optional.of("value-b"), subject.readLocked("b.txt"));
    }

    @Test
    public void shouldNotCreateDirectoryAtConstructionTime() {
        Assert.assertFalse(Files.exists(directory));
    }

    @Test
    public void shouldCreateDirectoryLazilyOnFirstWrite() {
        Assert.assertFalse(Files.exists(directory));
        subject.writeLocked("state.txt", "value");
        Assert.assertTrue(Files.exists(directory));
    }

    @Test
    public void shouldCreateDirectoryLazilyOnFirstRead() {
        Assert.assertFalse(Files.exists(directory));
        subject.readLocked("missing.txt");
        Assert.assertTrue(Files.exists(directory));
    }

    @Test
    public void shouldPassEmptyToTransformWhenFileIsNewAndWriteWhatItReturns() {
        subject.readAndWriteLocked("state.txt", current -> {
            Assert.assertEquals(Optional.empty(), current);
            return "initial";
        });
        Assert.assertEquals(Optional.of("initial"), subject.readLocked("state.txt"));
    }

    @Test
    public void shouldPassCurrentContentToTransformAndWriteWhatItReturns() {
        subject.writeLocked("state.txt", "before");
        subject.readAndWriteLocked("state.txt", current -> {
            Assert.assertEquals(Optional.of("before"), current);
            return "after";
        });
        Assert.assertEquals(Optional.of("after"), subject.readLocked("state.txt"));
    }

    @Test
    public void shouldDeleteAnExistingFile() {
        subject.writeLocked("state.txt", "value");
        subject.deleteLocked("state.txt");
        Assert.assertFalse(Files.exists(directory.resolve("state.txt")));
    }

    @Test
    public void shouldNotThrowWhenDeletingAFileThatDoesNotExist() {
        subject.deleteLocked("never-written.txt");
    }

    @Test
    public void shouldNotAffectOtherFilesWhenDeletingOne() {
        subject.writeLocked("a.txt", "value-a");
        subject.writeLocked("b.txt", "value-b");
        subject.deleteLocked("a.txt");
        Assert.assertEquals(Optional.empty(), subject.readLocked("a.txt"));
        Assert.assertEquals(Optional.of("value-b"), subject.readLocked("b.txt"));
    }

    @Test
    public void shouldCreateDirectoryLazilyOnFirstReadAndWrite() {
        Assert.assertFalse(Files.exists(directory));
        subject.readAndWriteLocked("state.txt", current -> "value");
        Assert.assertTrue(Files.exists(directory));
    }

    @Test
    public void shouldSerializeConcurrentThreadsInTheSameJvmOnTheSameFileWithoutThrowing() throws Exception {
        // FileChannel.lock() is JVM-wide: a second thread in this same process attempting to lock
        // a region already locked by this process fails immediately with
        // OverlappingFileLockException instead of blocking. Without an in-JVM lock in front of it,
        // concurrent access to the same file name from multiple threads would intermittently throw.
        final int threadCount = 16;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);
        final AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    subject.readAndWriteLocked("shared.txt", current -> current.orElse("") + index + ";");
                } catch (Exception e) {
                    failures.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        Assert.assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        Assert.assertEquals(0, failures.get());
        final String content = subject.readLocked("shared.txt").orElseThrow();
        for (int i = 0; i < threadCount; i++) {
            Assert.assertTrue("missing entry for thread " + i, content.contains(i + ";"));
        }
    }

    // -------------------------------------------------------------------------
    // Issue #123: writes go through a temp file + atomic rename, never a
    // truncate-then-write in place, and the OS lock lives on a dedicated
    // .lock file rather than the data file itself.
    // -------------------------------------------------------------------------

    @Test
    public void shouldNotLeaveATemporaryFileBehindAfterASuccessfulWrite() throws IOException {
        subject.writeLocked("state.txt", "value");
        assertNoStrayTempFiles();
    }

    @Test
    public void shouldNotLeaveATemporaryFileBehindAfterASuccessfulReadAndWrite() throws IOException {
        subject.readAndWriteLocked("state.txt", current -> "value");
        assertNoStrayTempFiles();
    }

    @Test
    public void shouldLockADedicatedFileSeparateFromTheDataFileItself() {
        subject.writeLocked("state.txt", "value");
        Assert.assertTrue("expected a dedicated .lock file alongside the data file",
            Files.exists(directory.resolve("state.txt.lock")));
    }

    @Test
    public void shouldDeleteTheLockFileWhenDeletingTheDataFile() {
        subject.writeLocked("state.txt", "value");
        subject.deleteLocked("state.txt");
        Assert.assertFalse(Files.exists(directory.resolve("state.txt")));
        Assert.assertFalse(Files.exists(directory.resolve("state.txt.lock")));
    }

    /**
     * The exact crash window issue #123 describes: a temp file was fully written but the process
     * died before the atomic rename ran. Since the real data file is never touched until that
     * rename succeeds, an orphaned temp file lying around must not affect it at all - the previous
     * content survives completely untouched, never truncated or partially overwritten.
     */
    @Test
    public void anOrphanedTempFileFromAnInterruptedWriteMustNotAffectThePreviousContent() throws IOException {
        subject.writeLocked("state.txt", "original");

        Files.writeString(directory.resolve("state.txt." + UUID.randomUUID() + ".tmp"), "orphaned-content",
            StandardCharsets.UTF_8);

        Assert.assertEquals(Optional.of("original"), subject.readLocked("state.txt"));
    }

    private void assertNoStrayTempFiles() throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            final List<Path> tempFiles = files.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList();
            Assert.assertTrue("expected no leftover .tmp files, found " + tempFiles, tempFiles.isEmpty());
        }
    }

}
