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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Issue #76: the primitive {@code FileFlowExecutionDumpRepository} builds its crash-recovery
 * claim on.
 */
public class ProcessScopedFileLockTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path lockFile() {
        return temporaryFolder.getRoot().toPath().resolve("nested").resolve("a.lock");
    }

    @Test
    public void shouldAcquireAFreeLockAndCreateItsParentDirectory() {
        final Optional<ProcessScopedFileLock> lock = ProcessScopedFileLock.tryAcquire(lockFile());
        Assert.assertTrue(lock.isPresent());
        Assert.assertTrue(java.nio.file.Files.exists(lockFile()));
        lock.get().close();
    }

    @Test
    public void shouldFailToAcquireALockAlreadyHeldByThisSameJvm() {
        final Path file = lockFile();
        final Optional<ProcessScopedFileLock> first = ProcessScopedFileLock.tryAcquire(file);
        Assert.assertTrue(first.isPresent());

        // A FileLock is scoped to the whole process, not the channel that acquired it: a second,
        // independent open()+tryLock() on the same path from this same JVM must fail too, not
        // silently succeed just because it is "the same process" that already holds it.
        final Optional<ProcessScopedFileLock> second = ProcessScopedFileLock.tryAcquire(file);
        Assert.assertTrue(second.isEmpty());

        first.get().close();
    }

    @Test
    public void shouldBeAcquirableAgainOnceReleased() {
        final Path file = lockFile();
        final ProcessScopedFileLock first = ProcessScopedFileLock.tryAcquire(file).orElseThrow();
        first.close();

        final Optional<ProcessScopedFileLock> second = ProcessScopedFileLock.tryAcquire(file);
        Assert.assertTrue(second.isPresent());
        second.get().close();
    }

    @Test
    public void closeShouldBeIdempotent() {
        final ProcessScopedFileLock lock = ProcessScopedFileLock.tryAcquire(lockFile()).orElseThrow();
        lock.close();
        lock.close(); // must not throw
    }

    @Test
    public void shouldReportWhetherItHasBeenHeldLongerThanAGivenDuration() throws InterruptedException {
        final ProcessScopedFileLock lock = ProcessScopedFileLock.tryAcquire(lockFile()).orElseThrow();
        try {
            Assert.assertFalse(lock.heldLongerThan(Duration.ofSeconds(10)));
            Thread.sleep(50);
            Assert.assertTrue(lock.heldLongerThan(Duration.ofMillis(20)));
        } finally {
            lock.close();
        }
    }

}
