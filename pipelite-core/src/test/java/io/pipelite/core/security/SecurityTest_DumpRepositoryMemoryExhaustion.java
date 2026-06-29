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
package io.pipelite.core.security;

import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.dump.FlowExecutionDumpInMemoryRepository;
import io.pipelite.core.flow.execution.dump.SerializedFlowExecutionDump;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Security tests for FlowExecutionDumpInMemoryRepository.
 *
 * Vulnerability #1 — poll() Does Not Remove (Implicit Leak):
 *   poll() returns the oldest dump but leaves it in the map.
 *   Removal is the caller's responsibility via remove(id).
 *   If the retry flow path fails after poll() (e.g., deserialization error),
 *   the dump is never removed and occupies memory indefinitely.
 *   A sustained stream of bad messages creates a permanent, growing leak.
 *
 * Vulnerability #2 — No Capacity Cap (Memory Exhaustion / DoS):
 *   The backing ConcurrentHashMap has no maximum size.
 *   An attacker who can trigger repeated processing failures (e.g., by sending
 *   malformed messages) causes the dump repository to grow without bound until
 *   the JVM heap is exhausted (OutOfMemoryError).
 */
public class SecurityTest_DumpRepositoryMemoryExhaustion {

    private static final int MAX_SAFE_CAPACITY = 10_000;

    private FlowExecutionDumpRepository repository;

    @Before
    public void setup() {
        repository = new FlowExecutionDumpInMemoryRepository();
    }

    /**
     * After poll(), the returned dump should no longer be retrievable.
     * A subsequent poll() on a single-entry repository should return empty.
     *
     * Expected: second poll() returns Optional.empty().
     * Actual:   second poll() returns the same dump — it was never removed.
     */
    @Test
    @Ignore
    public void pollShouldConsumeTheDump() {
        repository.save(SerializedFlowExecutionDump.createNow("id-1", "", "flow-a"));

        Optional<FlowExecutionDump> first = repository.poll();
        assertTrue("First poll() must return a dump", first.isPresent());

        // The dump should have been consumed by the first poll.
        // FAILS: poll() does not remove — second call returns the same entry.
        Optional<FlowExecutionDump> second = repository.poll();
        assertFalse(
            "Repository must be empty after the only dump has been polled",
            second.isPresent());
    }

    /**
     * When multiple dumps are present, polling one should not expose it again
     * on the next poll call.
     *
     * Expected: each poll() returns a distinct dump and shrinks the repository.
     * Actual:   the same (oldest) dump is returned on every poll() call.
     */
    @Test
    @Ignore
    public void consecutivePollsShouldReturnDistinctDumps() {
        repository.save(SerializedFlowExecutionDump.createNow("id-1", "", "flow-a"));
        repository.save(SerializedFlowExecutionDump.createNow("id-2", "", "flow-a"));

        Optional<FlowExecutionDump> first  = repository.poll();
        Optional<FlowExecutionDump> second = repository.poll();

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());

        // FAILS: both polls return "id-1" because poll() never removes
        assertFalse(
            "Consecutive poll() calls must return distinct dumps",
            first.get().getId().equals(second.get().getId()));
    }

    /**
     * The repository must enforce a maximum capacity to prevent heap exhaustion.
     * An attacker can induce repeated failures to fill the repository with dumps.
     *
     * Expected: repository refuses saves beyond MAX_SAFE_CAPACITY or evicts old entries.
     * Actual:   all entries are stored — no cap exists.
     */
    @Test
    @Ignore
    public void repositoryShouldEnforceMaximumCapacity() {
        int overLimit = MAX_SAFE_CAPACITY + 1;
        for (int i = 0; i < overLimit; i++) {
            repository.save(SerializedFlowExecutionDump.createNow("id-" + i, "", "flow"));
        }

        long storedCount = countStoredDumps(overLimit);

        // FAILS: storedCount == overLimit because there is no eviction or cap
        assertTrue(
            "Repository must cap stored dumps at " + MAX_SAFE_CAPACITY + " to prevent memory exhaustion",
            storedCount <= MAX_SAFE_CAPACITY);
    }

    private long countStoredDumps(int limit) {
        long count = 0;
        for (int i = 0; i < limit; i++) {
            if (repository.tryLoad("id-" + i).isPresent()) {
                count++;
            }
        }
        return count;
    }
}
