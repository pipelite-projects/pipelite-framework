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

import io.pipelite.common.support.fs.ProcessScopedFileLock;
import io.pipelite.core.flow.execution.FlowExecutionDump;
import io.pipelite.core.flow.execution.FlowExecutionDumpRepository;
import io.pipelite.core.flow.execution.FlowExecutionDumpStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

public class FileFlowExecutionDumpRepositoryTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path directory;
    private FlowExecutionDumpRepository subject;

    @Before
    public void setup() {
        directory = temporaryFolder.getRoot().toPath().resolve("retry");
        subject = new FileFlowExecutionDumpRepository(directory);
    }

    private static SerializedFlowExecutionDump aDump(String id, LocalDateTime creationTime) {
        final SerializedFlowExecutionDump dump = new SerializedFlowExecutionDump(id, "flow-hash", "a-flow", creationTime);
        dump.setSourceEndpointResource("a-source");
        dump.setLastExecutedProcessor("a-processor");
        dump.setFailedProcessor("the-failing-processor");
        dump.setAttemptNumber(2);
        dump.setStackTrace("java.lang.RuntimeException: boom\n\tat Somewhere.java:1");
        dump.setMaxAttempts(5);
        dump.setDeadLetterTarget("a-dead-letter-flow");
        dump.setExchangeData("c29tZS1wYXlsb2Fk", "base64");
        return dump;
    }

    @Test
    public void shouldReturnEmptyWhenLoadingAnUnknownId() {
        Assert.assertEquals(Optional.empty(), subject.tryLoad("unknown-id"));
    }

    @Test
    public void shouldRoundTripEveryFieldThroughSaveAndTryLoad() {

        final SerializedFlowExecutionDump saved = aDump("dump-1", LocalDateTime.of(2026, 9, 10, 12, 30, 0));
        saved.setNextAttemptTime(LocalDateTime.of(2026, 9, 10, 12, 30, 2));
        subject.save(saved);

        final FlowExecutionDump loaded = subject.tryLoad("dump-1").orElseThrow();

        Assert.assertEquals(saved.getId(), loaded.getId());
        Assert.assertEquals(saved.getCreationTime(), loaded.getCreationTime());
        Assert.assertEquals(saved.getFlowHash(), loaded.getFlowHash());
        Assert.assertEquals(saved.getFlowName(), loaded.getFlowName());
        Assert.assertEquals(saved.getSourceEndpointResource(), loaded.getSourceEndpointResource());
        Assert.assertEquals(saved.getLastExecutedProcessor(), loaded.getLastExecutedProcessor());
        Assert.assertEquals(saved.getFailedProcessor(), loaded.getFailedProcessor());
        Assert.assertEquals(saved.getAttemptNumber(), loaded.getAttemptNumber());
        Assert.assertEquals(saved.getStackTrace(), loaded.getStackTrace());
        Assert.assertEquals(saved.getMaxAttempts(), loaded.getMaxAttempts());
        Assert.assertEquals(saved.getDeadLetterTarget(), loaded.getDeadLetterTarget());
        Assert.assertEquals(saved.getExchangeData(), ((SerializedFlowExecutionDump) loaded).getExchangeData());
        Assert.assertEquals(saved.getEncoding(), ((SerializedFlowExecutionDump) loaded).getEncoding());
        Assert.assertEquals(saved.getNextAttemptTime(), loaded.getNextAttemptTime());
    }

    @Test
    public void shouldRoundTripANullNextAttemptTimeAsNull() {

        final SerializedFlowExecutionDump saved = aDump("dump-1b", LocalDateTime.now());
        subject.save(saved);

        final FlowExecutionDump loaded = subject.tryLoad("dump-1b").orElseThrow();

        Assert.assertNull("no backoff configured must round-trip as null, not a parse failure", loaded.getNextAttemptTime());
    }

    @Test
    public void shouldRoundTripNullDeadLetterTargetAndFailedProcessorAsNullNotEmptyString() {

        final SerializedFlowExecutionDump saved = aDump("dump-2", LocalDateTime.now());
        saved.setDeadLetterTarget(null);
        saved.setFailedProcessor(null);
        subject.save(saved);

        final FlowExecutionDump loaded = subject.tryLoad("dump-2").orElseThrow();

        Assert.assertNull("deadLetterTarget must round-trip as null, not empty string", loaded.getDeadLetterTarget());
        Assert.assertNull("failedProcessor must round-trip as null, not empty string", loaded.getFailedProcessor());
    }

    @Test
    public void shouldReturnTheOldestPendingDumpFromPoll() {

        subject.save(aDump("newer", LocalDateTime.of(2026, 9, 10, 12, 0, 0)));
        subject.save(aDump("oldest", LocalDateTime.of(2026, 9, 10, 10, 0, 0)));
        subject.save(aDump("middle", LocalDateTime.of(2026, 9, 10, 11, 0, 0)));

        final FlowExecutionDump polled = subject.poll().orElseThrow();

        Assert.assertEquals("oldest", polled.getId());
    }

    @Test
    public void shouldReturnEmptyFromPollWhenNothingWasEverSaved() {
        Assert.assertEquals(Optional.empty(), subject.poll());
    }

    // -------------------------------------------------------------------------
    // Issue #95: a dump's own backoff schedule gates its eligibility for poll()
    // -------------------------------------------------------------------------

    @Test
    public void shouldNotReturnAPendingDumpFromPollBeforeItsNextAttemptTime() {
        final SerializedFlowExecutionDump dump = aDump("not-due-yet", LocalDateTime.now());
        dump.setNextAttemptTime(LocalDateTime.now().plusHours(1));
        subject.save(dump);

        Assert.assertEquals(Optional.empty(), subject.poll());
    }

    @Test
    public void shouldReturnAPendingDumpFromPollOnceItsNextAttemptTimeHasPassed() {
        final SerializedFlowExecutionDump dump = aDump("now-due", LocalDateTime.now());
        dump.setNextAttemptTime(LocalDateTime.now().minusSeconds(1));
        subject.save(dump);

        Assert.assertEquals("now-due", subject.poll().orElseThrow().getId());
    }

    @Test
    public void givenTheOldestDumpIsNotYetDue_thenPollSkipsItForTheNextOldestThatIs() {
        final SerializedFlowExecutionDump notYetDue = aDump("older-not-due", LocalDateTime.of(2026, 9, 10, 10, 0, 0));
        notYetDue.setNextAttemptTime(LocalDateTime.now().plusHours(1));
        subject.save(notYetDue);
        subject.save(aDump("newer-due", LocalDateTime.of(2026, 9, 10, 11, 0, 0)));

        Assert.assertEquals("newer-due", subject.poll().orElseThrow().getId());
    }

    @Test
    public void shouldNoLongerBeFoundAfterRemove() {

        subject.save(aDump("dump-3", LocalDateTime.now()));
        subject.remove("dump-3");

        Assert.assertEquals(Optional.empty(), subject.tryLoad("dump-3"));
        Assert.assertEquals(Optional.empty(), subject.poll());
    }

    @Test
    public void shouldNotThrowWhenRemovingAnUnknownId() {
        subject.remove("never-saved");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectSavingAFlowExecutionDumpThatIsNotSerializedFlowExecutionDump() {
        subject.save(new DefaultFlowExecutionDump("id", "hash", "flow", LocalDateTime.now()));
    }

    // -------------------------------------------------------------------------
    // Issue #76: tryClaim's exclusivity, and recovering it after a crash
    // -------------------------------------------------------------------------

    @Test
    public void shouldClaimAPendingDumpOnlyOnce() {
        subject.save(aDump("dump-4", LocalDateTime.now()));

        Assert.assertTrue(subject.tryClaim("dump-4"));
        Assert.assertFalse("already claimed, must not be claimable a second time", subject.tryClaim("dump-4"));
    }

    @Test
    public void shouldNotClaimADumpThatWasNeverSaved() {
        Assert.assertFalse(subject.tryClaim("never-saved"));
    }

    /**
     * A real regression, found manually exercising the food-delivery example after #123 shipped:
     * a duplicate tryClaim(id) call on an id that was already resolved and removed used to
     * resurrect it as a 0-byte .dump file, since LockedFileStore's own "nothing to claim" no-op
     * relied on writing "" being harmless under the old truncate-in-place write - it is not under
     * the new atomic-replace one, which was fixed to require null for a genuine no-op instead.
     */
    @Test
    public void aSecondTryClaimOnAnIdThatWasNeverSavedMustNotCreateAStrayDumpFile() {
        Assert.assertFalse(subject.tryClaim("never-saved"));
        Assert.assertFalse(Files.exists(directory.resolve("never-saved.dump")));
        Assert.assertFalse(Files.exists(directory.resolve("never-saved.claim")));
    }

    /**
     * A second, distinct regression found on the same exercise, after the fix above: even once the
     * 0-byte .dump file was gone, a duplicate tryClaim(id) on an id whose dump was already resolved
     * and removed still permanently resurrected a {@code .claim} file (and, via {@link
     * ProcessScopedFileLock}'s own {@code CREATE}-on-open, a {@code .dump.lock} file too) - exactly
     * the two orphans reported manually against the food-delivery example. Fixed by checking the
     * dump file's existence before ever touching the claim file, not after.
     */
    @Test
    public void aTryClaimOnAnAlreadyRemovedDumpMustNotResurrectIt() {
        subject.save(aDump("dump-4b", LocalDateTime.now()));
        subject.remove("dump-4b");

        Assert.assertFalse(subject.tryClaim("dump-4b"));
        Assert.assertFalse(Files.exists(directory.resolve("dump-4b.dump")));
        Assert.assertFalse("must not resurrect the claim file for an id nothing will ever revisit again",
            Files.exists(directory.resolve("dump-4b.claim")));
        Assert.assertFalse("must not resurrect the dump file's own lock file either",
            Files.exists(directory.resolve("dump-4b.dump.lock")));
        Assert.assertEquals(Optional.empty(), subject.tryLoad("dump-4b"));
    }

    @Test
    public void shouldNoLongerReturnAClaimedDumpFromPoll() {
        subject.save(aDump("dump-5", LocalDateTime.now()));
        subject.tryClaim("dump-5");

        Assert.assertEquals(Optional.empty(), subject.poll());
    }

    /**
     * The core of #76's fix: exclusivity comes from an OS-level lock this repository holds open
     * for the whole claim, not from the dump's own {@code status} field, which is kept only as a
     * fast filter for {@link FlowExecutionDumpRepository#poll()}. Resetting it externally - as a
     * bug elsewhere, or a dump written by code that never knew about the lock, might do - must not
     * reopen a claim that is still genuinely held.
     */
    @Test
    public void givenAClaimIsStillHeld_thenResettingTheDumpsStatusOnDiskDoesNotReopenIt() {
        final SerializedFlowExecutionDump dump = aDump("dump-6", LocalDateTime.now());
        subject.save(dump);
        Assert.assertTrue(subject.tryClaim("dump-6"));

        dump.setStatus(FlowExecutionDumpStatus.PENDING);
        subject.save(dump);

        Assert.assertFalse("the still-open lock must refuse a second claim regardless of the status field",
            subject.tryClaim("dump-6"));
    }

    @Test
    public void givenTwoRepositoryInstancesOverTheSameDirectory_thenOnlyOneCanClaimTheSameDump() {
        final FlowExecutionDumpRepository other = new FileFlowExecutionDumpRepository(directory);
        subject.save(aDump("shared", LocalDateTime.now()));

        Assert.assertTrue(subject.tryClaim("shared"));
        Assert.assertFalse("a second instance over the same directory must not win the same claim, " +
            "the same way two process instances sharing it must not", other.tryClaim("shared"));
    }

    @Test
    public void shouldDeleteTheClaimFileOnRemove() {
        subject.save(aDump("dump-7", LocalDateTime.now()));
        subject.tryClaim("dump-7");

        subject.remove("dump-7");

        Assert.assertFalse(Files.exists(directory.resolve("dump-7.claim")));
    }

    /**
     * The whole point of the OS-level lock over a timeout-based lease (issue #76, Option B over
     * Option A in the design discussion): once it is actually free, recovery is immediate, with no
     * safety-net duration to wait out - that duration exists only for the narrower case in the
     * next test, a thread dying without the process dying with it.
     */
    @Test
    public void givenAnExternallyHeldClaimIsReleased_thenTheNextAttemptSucceedsImmediately() {
        final SerializedFlowExecutionDump dump = aDump("crashed", LocalDateTime.now());
        subject.save(dump);

        // Stands in for a different process's still-open claim on this same directory: an
        // independent OS-level lock on the claim file, with the dump's own status flipped the same
        // way tryClaim would - without ever going through `subject`, so `subject` has no
        // bookkeeping of its own to reconcile once this is released.
        final ProcessScopedFileLock otherProcessClaim =
            ProcessScopedFileLock.tryAcquire(directory.resolve("crashed.claim")).orElseThrow();
        dump.setStatus(FlowExecutionDumpStatus.IN_PROGRESS);
        subject.save(dump);

        Assert.assertFalse("a live claim, even one this repository instance did not itself open, must be respected",
            subject.tryClaim("crashed"));

        // The crash: the OS releases the lock the instant the holding process terminates, with no
        // notice to anyone and no timeout to wait out.
        otherProcessClaim.close();

        Assert.assertTrue("once the OS lock is actually free, a claim must succeed immediately, with no waiting",
            subject.tryClaim("crashed"));
    }

    /**
     * The narrower case Option B alone cannot recover from: the process stays alive but the
     * specific thread that opened the claim never releases it. This repository's own periodic
     * self-cleanup, piggybacked on {@link FlowExecutionDumpRepository#poll()}, is what closes that
     * gap - see {@code FileFlowExecutionDumpRepository}'s own Javadoc.
     */
    @Test
    public void givenAClaimIsHeldPastTheSafetyNetDuration_thenPollReclaimsItAutomatically() throws InterruptedException {
        final FlowExecutionDumpRepository shortSafetyNet =
            new FileFlowExecutionDumpRepository(directory, Duration.ofMillis(30));
        shortSafetyNet.save(aDump("abandoned", LocalDateTime.now()));
        Assert.assertTrue(shortSafetyNet.tryClaim("abandoned"));

        Thread.sleep(80);

        final FlowExecutionDump polled = shortSafetyNet.poll().orElseThrow();
        Assert.assertEquals("abandoned", polled.getId());
        Assert.assertTrue("reclaimed by the same process's own safety net, it must be claimable again",
            shortSafetyNet.tryClaim("abandoned"));
    }

    @Test
    public void givenAClaimIsWellWithinTheSafetyNetDuration_thenPollDoesNotReclaimIt() {
        final FlowExecutionDumpRepository generousSafetyNet =
            new FileFlowExecutionDumpRepository(directory, Duration.ofHours(1));
        generousSafetyNet.save(aDump("in-progress", LocalDateTime.now()));
        Assert.assertTrue(generousSafetyNet.tryClaim("in-progress"));

        Assert.assertEquals("still within the safety net, not abandoned", Optional.empty(), generousSafetyNet.poll());
    }

}
