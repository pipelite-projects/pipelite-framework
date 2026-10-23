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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * What every {@link BarrierRepository} does, whatever it keeps the state in: an exchange waits for the flows it
 * awaits and is released exactly once, in whichever order the waiter arrives and the flows complete.
 */
public abstract class BarrierRepositoryContractTest {

    protected static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");
    protected static final Instant LATER = NOW.plusSeconds(3600);

    protected BarrierRepository repository;

    protected abstract BarrierRepository create();

    @Before
    public void setup() {
        repository = create();
    }

    protected static Pending pending(String flow, String key) {
        return new Pending(flow, key);
    }

    protected static Waiter waiter(String id, Pending... pending) {
        return waiter(id, NOW.plusSeconds(600), pending);
    }

    protected static Waiter waiter(String id, Instant expiresAt, Pending... pending) {
        return Waiter.waiting(id, "ship-order/preparation", "ship-order", "preparation", "serialized-exchange-of-" + id,
            "base64", new LinkedHashSet<>(List.of(pending)), NOW, expiresAt);
    }

    private static List<String> ids(List<Waiter> waiters) {
        return waiters.stream().map(Waiter::id).toList();
    }

    @Test
    public void givenNothingHasCompleted_thenTheWaiterIsHeldWithEverythingItWaitsFor() {
        final Waiter waiter = waiter("w-1", pending("step-b", "o-1"), pending("step-c", "o-1"));

        final WaitDisposition disposition = repository.arrive(waiter);

        Assert.assertEquals(WaitDisposition.Kind.HOLD, disposition.kind());
        final Waiter stored = repository.tryLoad("w-1").orElseThrow();
        Assert.assertEquals(Waiter.Status.WAITING, stored.status());
        Assert.assertEquals(waiter.pending(), stored.pending());
        Assert.assertEquals("serialized-exchange-of-w-1", stored.exchangeData());
    }

    @Test
    public void givenTheLastFlowCompletes_thenTheWaiterIsReleased() {
        repository.arrive(waiter("w-1", pending("step-b", "o-1"), pending("step-c", "o-1")));

        Assert.assertTrue("one of two is not enough", repository.complete("step-b", "o-1", LATER).isEmpty());
        Assert.assertEquals(Set.of(pending("step-c", "o-1")), repository.tryLoad("w-1").orElseThrow().pending());

        final List<Waiter> released = repository.complete("step-c", "o-1", LATER);

        Assert.assertEquals(List.of("w-1"), ids(released));
        Assert.assertEquals(Waiter.Status.RELEASED, released.get(0).status());
        Assert.assertEquals(Waiter.Status.RELEASED, repository.tryLoad("w-1").orElseThrow().status());
    }

    @Test
    public void givenAnotherKeyCompletes_thenTheWaiterKeepsWaiting() {
        repository.arrive(waiter("w-1", pending("step-b", "o-1")));

        Assert.assertTrue(repository.complete("step-b", "o-2", LATER).isEmpty());

        Assert.assertEquals(Waiter.Status.WAITING, repository.tryLoad("w-1").orElseThrow().status());
    }

    @Test
    public void givenSeveralWaitersForTheSamePair_thenOneCompletionReleasesAllOfThem() {
        repository.arrive(waiter("w-1", pending("step-b", "o-1")));
        repository.arrive(waiter("w-2", pending("step-b", "o-1")));
        repository.arrive(waiter("w-3", pending("step-b", "o-9")));

        Assert.assertEquals(List.of("w-1", "w-2"), ids(repository.complete("step-b", "o-1", LATER)));
        Assert.assertEquals(Waiter.Status.WAITING, repository.tryLoad("w-3").orElseThrow().status());
    }

    @Test
    public void givenEverythingHadAlreadyCompleted_thenTheWaiterProceedsAndIsNotStored() {
        repository.complete("step-b", "o-1", LATER);
        repository.complete("step-c", "o-1", LATER);

        final WaitDisposition disposition = repository.arrive(waiter("w-1", pending("step-b", "o-1"), pending("step-c", "o-1")));

        Assert.assertEquals(WaitDisposition.Kind.PROCEED, disposition.kind());
        Assert.assertTrue(repository.tryLoad("w-1").isEmpty());
    }

    @Test
    public void givenOneFlowHadCompleted_thenTheWaiterIsHeldForTheOtherOnly() {
        repository.complete("step-b", "o-1", LATER);

        final WaitDisposition disposition = repository.arrive(waiter("w-1", pending("step-b", "o-1"), pending("step-c", "o-1")));

        Assert.assertEquals(WaitDisposition.Kind.HOLD, disposition.kind());
        Assert.assertEquals(Set.of(pending("step-c", "o-1")), repository.tryLoad("w-1").orElseThrow().pending());
    }

    @Test
    public void givenAFlowHadAlreadyFailed_thenTheWaiterFailsAtOnceNamingIt() {
        repository.fail("step-c", "o-1", LATER);

        final WaitDisposition disposition = repository.arrive(waiter("w-1", pending("step-b", "o-1"), pending("step-c", "o-1")));

        Assert.assertEquals(WaitDisposition.Kind.FAIL, disposition.kind());
        Assert.assertEquals(pending("step-c", "o-1"), disposition.failedBy());
        Assert.assertTrue(repository.tryLoad("w-1").isEmpty());
    }

    @Test
    public void givenAFlowFailsWhileTheWaiterWaits_thenItFailsWithoutWaitingForTheOthers() {
        repository.arrive(waiter("w-1", pending("step-b", "o-1"), pending("step-c", "o-1")));

        final List<Waiter> failed = repository.fail("step-b", "o-1", LATER);

        Assert.assertEquals(List.of("w-1"), ids(failed));
        Assert.assertEquals(Waiter.Status.FAILED, failed.get(0).status());
        Assert.assertEquals(pending("step-b", "o-1"), failed.get(0).failedBy());
        Assert.assertTrue("the other flow completing later releases nobody",
            repository.complete("step-c", "o-1", LATER).isEmpty());
        Assert.assertEquals(Waiter.Status.FAILED, repository.tryLoad("w-1").orElseThrow().status());
    }

    @Test
    public void givenTheSameArrivalReplayed_thenItIsNotStoredTwice() {
        final Waiter waiter = waiter("w-1", pending("step-b", "o-1"), pending("step-c", "o-1"));
        repository.arrive(waiter);
        repository.complete("step-b", "o-1", LATER);

        final WaitDisposition replay = repository.arrive(waiter);

        Assert.assertEquals(WaitDisposition.Kind.HOLD, replay.kind());
        Assert.assertEquals("what it already lost is not asked again",
            Set.of(pending("step-c", "o-1")), repository.tryLoad("w-1").orElseThrow().pending());
    }

    @Test
    public void givenAWaiterAlreadyReleasedOrFailed_thenItsReplayIsDiscarded() {
        final Waiter released = waiter("w-1", pending("step-b", "o-1"));
        repository.arrive(released);
        repository.complete("step-b", "o-1", LATER);
        final Waiter failed = waiter("w-2", pending("step-c", "o-2"));
        repository.arrive(failed);
        repository.fail("step-c", "o-2", LATER);

        Assert.assertEquals(WaitDisposition.Kind.DISCARD, repository.arrive(released).kind());
        Assert.assertEquals(WaitDisposition.Kind.DISCARD, repository.arrive(failed).kind());
    }

    @Test
    public void givenTheFirstOutcomeOfAPair_thenItStaysAndNothingIsReturnedTwice() {
        repository.arrive(waiter("w-1", pending("step-b", "o-1")));

        Assert.assertEquals(1, repository.complete("step-b", "o-1", LATER).size());
        Assert.assertTrue(repository.complete("step-b", "o-1", LATER).isEmpty());
        Assert.assertTrue("a failure after a completion does not take it back", repository.fail("step-b", "o-1", LATER).isEmpty());
        Assert.assertEquals(WaitDisposition.Kind.PROCEED,
            repository.arrive(waiter("w-2", pending("step-b", "o-1"))).kind());
    }

    @Test
    public void givenResolvedWaiters_thenTheyAreFoundUntilTheFlowDealsWithThem() {
        repository.arrive(waiter("w-1", pending("step-b", "o-1")));
        repository.arrive(waiter("w-2", pending("step-c", "o-1")));
        repository.arrive(waiter("w-3", pending("step-d", "o-1")));
        repository.complete("step-b", "o-1", LATER);
        repository.fail("step-c", "o-1", LATER);

        Assert.assertEquals(List.of("w-1", "w-2"), ids(repository.findResolved()));

        repository.remove("w-1");
        Assert.assertEquals(List.of("w-2"), ids(repository.findResolved()));
        Assert.assertTrue(repository.tryLoad("w-1").isEmpty());
        repository.remove("never-existed");   // forgetting what is not there is harmless
    }

    @Test
    public void givenWaitersThatExpireAtDifferentTimes_thenOnlyTheOnesStillWaitingAndDueAreFound() {
        repository.arrive(waiter("early", NOW.plusSeconds(60), pending("step-b", "o-1")));
        repository.arrive(waiter("late", NOW.plusSeconds(7200), pending("step-b", "o-2")));
        repository.arrive(waiter("released", NOW.plusSeconds(60), pending("step-b", "o-3")));
        repository.complete("step-b", "o-3", LATER);

        Assert.assertEquals(List.of("early"), ids(repository.findExpired(NOW.plusSeconds(3600))));
        Assert.assertTrue(repository.findExpired(NOW).isEmpty());
    }

    @Test
    public void givenAnExpiredWaiterIsRemoved_thenALaterCompletionReleasesNobody() {
        repository.arrive(waiter("w-1", NOW.plusSeconds(60), pending("step-b", "o-1")));
        repository.remove("w-1");

        Assert.assertTrue(repository.complete("step-b", "o-1", LATER).isEmpty());
    }

    @Test
    public void givenCompletionsPastTheirRetention_thenTheyAreForgottenAndTheOthersStay() {
        repository.complete("step-b", "old", NOW.plusSeconds(60));
        repository.complete("step-b", "kept", NOW.plusSeconds(7200));

        Assert.assertEquals(1, repository.purgeCompletions(NOW.plusSeconds(3600)));

        Assert.assertEquals("a forgotten completion is waited for again",
            WaitDisposition.Kind.HOLD, repository.arrive(waiter("w-1", pending("step-b", "old"))).kind());
        Assert.assertEquals(WaitDisposition.Kind.PROCEED, repository.arrive(waiter("w-2", pending("step-b", "kept"))).kind());
    }

    @Test
    public void givenTheWaiterAndTheCompletionsRaceEachOther_thenTheExchangeGoesOnExactlyOnce() throws Exception {
        final int rounds = 200;
        final ExecutorService pool = Executors.newFixedThreadPool(3);
        final List<String> wrong = Collections.synchronizedList(new ArrayList<>());
        try {
            for (int round = 0; round < rounds; round++) {
                final String key = "o-" + round;
                final Waiter waiter = waiter("w-" + round, pending("step-b", key), pending("step-c", key));
                final CountDownLatch start = new CountDownLatch(1);
                final List<Object> goneOn = Collections.synchronizedList(new ArrayList<>());
                final List<Runnable> actions = List.of(
                    () -> {
                        if (repository.arrive(waiter).kind() == WaitDisposition.Kind.PROCEED) {
                            goneOn.add("proceed");
                        }
                    },
                    () -> repository.complete("step-b", key, LATER).forEach(goneOn::add),
                    () -> repository.complete("step-c", key, LATER).forEach(goneOn::add));
                final List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
                for (Runnable action : actions) {
                    futures.add(pool.submit(() -> {
                        try {
                            start.await();
                            action.run();
                        } catch (Throwable throwable) {
                            wrong.add(key + " failed: " + throwable);
                        }
                    }));
                }
                start.countDown();
                for (java.util.concurrent.Future<?> future : futures) {
                    future.get(30, TimeUnit.SECONDS);
                }
                if (goneOn.size() != 1) {
                    wrong.add(key + " went on " + goneOn.size() + " times: " + goneOn);
                }
            }
        } finally {
            pool.shutdownNow();
        }

        Assert.assertTrue(wrong.size() + " rounds were wrong, for instance " + wrong.stream().limit(3).toList(), wrong.isEmpty());
    }
}
