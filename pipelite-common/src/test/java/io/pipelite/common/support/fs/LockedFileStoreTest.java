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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

}
