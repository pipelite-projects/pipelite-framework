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
package io.pipelite.core.flow.barrier;

import io.pipelite.core.exchange.internal.HeadersImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Issue #150: the barriers on Pipelite Home survive a restart and are changed atomically per key. */
public class BarrierFileRepositoryTest {

    private static final String BARRIER = "flow/barrier-step";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path directory;
    private BarrierFileRepository repository;

    @Before
    public void setup() {
        directory = temporaryFolder.getRoot().toPath().resolve("state").resolve("barrier");
        repository = new BarrierFileRepository(directory);
    }

    private static BarrierState open(String key) {
        return BarrierState.open(BARRIER, key, Instant.parse("2026-10-13T10:00:00Z"), Duration.ofMinutes(10));
    }

    private static BarrierContribution contribution(String contributor, Object payload) {
        final HeadersImpl headers = new HeadersImpl();
        headers.putHeader("from", contributor);
        return new BarrierContribution(contributor, "message-" + contributor, headers, payload);
    }

    private long files() throws IOException {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (Stream<Path> list = Files.list(directory)) {
            return list.filter(path -> path.getFileName().toString().endsWith(".barrier")).count();
        }
    }

    @Test
    public void givenAStateWasWritten_thenANewRepositoryOnTheSameDirectoryReadsItBack() {
        repository.update(BARRIER, "o-1", () -> open("o-1"), state -> {
            state.add(contribution("record", "payload-1"));
            return null;
        });

        final BarrierFileRepository afterRestart = new BarrierFileRepository(directory);
        final BarrierState state = afterRestart.tryLoad(BARRIER, "o-1").orElseThrow();

        Assert.assertEquals(BarrierState.Status.OPEN, state.getStatus());
        Assert.assertEquals(List.of("record"), new ArrayList<>(state.contributors()));
        final BarrierContribution kept = state.contributions().get(0);
        Assert.assertEquals("payload-1", kept.getPayload());
        Assert.assertEquals("record", kept.getHeaders().tryGetHeader("from").orElseThrow());
        Assert.assertEquals("message-record", kept.getMessageId());
        Assert.assertEquals(Instant.parse("2026-10-13T10:10:00Z"), state.getExpiresAt());
    }

    @Test
    public void givenTheStateIsClosedAndDone_thenTheStatusSurvivesToo() {
        repository.update(BARRIER, "o-1", () -> open("o-1"), state -> {
            state.add(contribution("record", "p"));
            state.close(BarrierState.Status.COMPLETED, "record");
            return null;
        });
        Assert.assertEquals(BarrierState.Status.COMPLETED, repository.tryLoad(BARRIER, "o-1").orElseThrow().getStatus());
        Assert.assertEquals("record", repository.tryLoad(BARRIER, "o-1").orElseThrow().getClosedBy());

        repository.update(BARRIER, "o-1", () -> null, state -> {
            state.markDone();
            return null;
        });

        final BarrierState done = new BarrierFileRepository(directory).tryLoad(BARRIER, "o-1").orElseThrow();
        Assert.assertEquals(BarrierState.Status.DONE, done.getStatus());
        Assert.assertTrue(done.contributions().isEmpty());
    }

    @Test
    public void givenNoStateAndNothingToCreate_thenNothingIsCreatedNorRun() throws IOException {
        final Object result = repository.update(BARRIER, "absent", () -> null, state -> "ran");

        Assert.assertNull(result);
        Assert.assertTrue(repository.tryLoad(BARRIER, "absent").isEmpty());
        Assert.assertEquals(0, files());
    }

    @Test
    public void givenAnActionThatFails_thenTheStateIsLeftAsItWas() {
        repository.update(BARRIER, "o-1", () -> open("o-1"), state -> {
            state.add(contribution("record", "p"));
            return null;
        });

        try {
            repository.update(BARRIER, "o-1", () -> open("o-1"), state -> {
                state.add(contribution("check", "p"));
                throw new IllegalStateException("boom");
            });
            Assert.fail("the failure of the action must reach the caller");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("boom", expected.getMessage());
        }

        Assert.assertEquals(List.of("record"), new ArrayList<>(repository.tryLoad(BARRIER, "o-1").orElseThrow().contributors()));
    }

    @Test
    public void givenAPayloadThatIsNotSerializable_thenItFailsAndNothingIsWritten() throws IOException {
        try {
            repository.update(BARRIER, "o-1", () -> open("o-1"), state -> {
                state.add(contribution("record", new Object()));
                return null;
            });
            Assert.fail("a payload that cannot be written cannot wait at a barrier");
        } catch (RuntimeException expected) {
            // the serialization says so
        }

        Assert.assertEquals(0, files());
    }

    @Test
    public void givenKeysWithAnyCharacter_thenEachHasItsOwnFile() throws IOException {
        for (String key : List.of("a/b", "a\\b", "ordine n. 1: €", "../../etc", "x y")) {
            repository.update(BARRIER, key, () -> open(key), state -> null);
        }

        Assert.assertEquals(5, files());
        Assert.assertTrue(repository.tryLoad(BARRIER, "../../etc").isPresent());
        repository.remove(BARRIER, "a/b");
        Assert.assertTrue(repository.tryLoad(BARRIER, "a/b").isEmpty());
        Assert.assertEquals(4, files());
    }

    @Test
    public void givenTheStatesExpireAtDifferentTimes_thenOnlyTheOnesDueAreFound() {
        repository.update(BARRIER, "early", () -> BarrierState.open(BARRIER, "early", Instant.parse("2026-10-13T10:00:00Z"), Duration.ofMinutes(5)), state -> null);
        repository.update(BARRIER, "late", () -> BarrierState.open(BARRIER, "late", Instant.parse("2026-10-13T10:00:00Z"), Duration.ofHours(5)), state -> null);

        final List<BarrierState> expired = repository.findExpired(Instant.parse("2026-10-13T10:30:00Z"));

        Assert.assertEquals(1, expired.size());
        Assert.assertEquals("early", expired.get(0).getKey());
    }

    @Test
    public void givenManyThreadsUpdatingTheSameKey_thenNoUpdateIsLost() throws Exception {
        final int threads = 12;
        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        try {
            for (int i = 0; i < threads; i++) {
                final String contributor = "step-" + i;
                pool.submit(() -> {
                    try {
                        start.await();
                        repository.update(BARRIER, "o-1", () -> open("o-1"), state -> {
                            state.add(contribution(contributor, "p"));
                            return null;
                        });
                    } catch (Throwable t) {
                        errors.add(t);
                    }
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            Assert.assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        }

        Assert.assertTrue(errors.toString(), errors.isEmpty());
        Assert.assertEquals(threads, repository.tryLoad(BARRIER, "o-1").orElseThrow().contributors().size());
    }
}
