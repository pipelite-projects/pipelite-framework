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
package io.pipelite.components.file;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class FileTailStateStoreTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path stateDirectory;
    private FileTailStateStore subject;

    @Before
    public void setup() {
        stateDirectory = temporaryFolder.getRoot().toPath().resolve("state");
        subject = new FileTailStateStore(stateDirectory);
    }

    @Test
    public void shouldReturnZeroWhenStateFileDoesNotExistYet() {
        final TailState state = subject.load("/var/log/nonexistent.log");
        Assert.assertEquals(0L, state.getOffset());
        Assert.assertEquals(0L, state.getSkippedLines());
    }

    @Test
    public void shouldRoundTripSaveAndLoad() {
        final String resourcePath = "/var/log/app.log";
        subject.save(resourcePath, new TailState(12345L, 2L));
        TailState loaded = subject.load(resourcePath);
        Assert.assertEquals(12345L, loaded.getOffset());
        Assert.assertEquals(2L, loaded.getSkippedLines());

        subject.save(resourcePath, new TailState(67890L, 3L));
        loaded = subject.load(resourcePath);
        Assert.assertEquals(67890L, loaded.getOffset());
        Assert.assertEquals(3L, loaded.getSkippedLines());
    }

    @Test
    public void shouldDefaultSkippedLinesToZeroForLegacySingleLineStateFile() throws Exception {
        final String resourcePath = "/var/log/legacy.log";
        subject.save(resourcePath, new TailState(1L, 0L));

        // Simulate a state file written before skippedLines existed: a single line with just the offset.
        final Path stateFile = stateDirectory.resolve(sha256Hex(resourcePath) + ".state");
        Files.writeString(stateFile, "42", StandardCharsets.UTF_8);

        final TailState loaded = subject.load(resourcePath);
        Assert.assertEquals(42L, loaded.getOffset());
        Assert.assertEquals(0L, loaded.getSkippedLines());
    }

    @Test
    public void shouldNameStateFileAfterSha256OfResourcePath() throws Exception {
        final String resourcePath = "/var/log/app.log";
        subject.save(resourcePath, new TailState(1L, 0L));

        final String expectedSha256 = sha256Hex(resourcePath);
        final Path expectedStateFile = stateDirectory.resolve(expectedSha256 + ".state");
        Assert.assertTrue(Files.exists(expectedStateFile));
    }

    @Test
    public void shouldWriteIndexPropertiesMappingSha256ToOriginalPath() throws Exception {
        final String resourcePath = "/var/log/app.log";
        subject.save(resourcePath, new TailState(1L, 0L));

        final Path indexFile = stateDirectory.resolve("index.properties");
        Assert.assertTrue(Files.exists(indexFile));

        final Properties properties = new Properties();
        try (var in = Files.newInputStream(indexFile)) {
            properties.load(in);
        }
        final String expectedSha256 = sha256Hex(resourcePath);
        Assert.assertEquals(resourcePath, properties.getProperty(expectedSha256));
    }

    @Test
    public void shouldSurviveConcurrentSavesWithoutCorruptingState() throws Exception {
        final String resourcePath = "/var/log/concurrent.log";
        final int threadCount = 8;
        final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(threadCount);
        final AtomicLong maxOffsetSubmitted = new AtomicLong(0);

        for (int i = 1; i <= threadCount; i++) {
            final long offset = i * 100L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    subject.save(resourcePath, new TailState(offset, 0L));
                    maxOffsetSubmitted.updateAndGet(current -> Math.max(current, offset));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        Assert.assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // No exception/corruption: the persisted value must be one of the submitted offsets.
        final long persisted = subject.load(resourcePath).getOffset();
        Assert.assertTrue(persisted > 0 && persisted <= maxOffsetSubmitted.get());
    }

    @Test
    public void shouldNotLoseIndexEntriesWhenDifferentNewResourcesAreSavedConcurrently() throws Exception {
        final int resourceCount = 16;
        final ExecutorService executor = Executors.newFixedThreadPool(resourceCount);
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(resourceCount);

        for (int i = 0; i < resourceCount; i++) {
            final String resourcePath = "/var/log/resource-" + i + ".log";
            executor.submit(() -> {
                try {
                    startLatch.await();
                    subject.save(resourcePath, new TailState(1L, 0L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        Assert.assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        final Path indexFile = stateDirectory.resolve("index.properties");
        final Properties properties = new Properties();
        try (var in = Files.newInputStream(indexFile)) {
            properties.load(in);
        }
        for (int i = 0; i < resourceCount; i++) {
            final String resourcePath = "/var/log/resource-" + i + ".log";
            Assert.assertEquals(resourcePath, properties.getProperty(sha256Hex(resourcePath)));
        }
    }

    private static String sha256Hex(String value) throws Exception {
        final MessageDigest digest = MessageDigest.getInstance("SHA-256");
        final byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        final StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

}
