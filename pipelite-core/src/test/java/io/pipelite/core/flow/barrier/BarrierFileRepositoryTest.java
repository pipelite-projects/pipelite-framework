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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** The barriers on Pipelite Home do what every repository does, and survive a restart. */
public class BarrierFileRepositoryTest extends BarrierRepositoryContractTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Path directory() {
        return temporaryFolder.getRoot().toPath().resolve("state").resolve("barrier");
    }

    @Override
    protected BarrierRepository create() {
        return new BarrierFileRepository(directory());
    }

    private long files(String subdirectory) throws IOException {
        final Path path = directory().resolve(subdirectory);
        if (!Files.isDirectory(path)) {
            return 0;
        }
        try (Stream<Path> list = Files.list(path)) {
            return list.filter(file -> !file.getFileName().toString().endsWith(".lock")).count();
        }
    }

    @Test
    public void givenAWaiterAndACompletion_thenANewRepositoryOnTheSameDirectoryFindsThem() {
        repository.arrive(waiter("w-1", pending("step-b", "o-1"), pending("step-c", "o-1")));
        repository.complete("step-b", "o-1", LATER);

        final BarrierRepository afterRestart = new BarrierFileRepository(directory());

        final Waiter waiter = afterRestart.tryLoad("w-1").orElseThrow();
        Assert.assertEquals(Waiter.Status.WAITING, waiter.status());
        Assert.assertEquals(Set.of(pending("step-c", "o-1")), waiter.pending());
        Assert.assertEquals("serialized-exchange-of-w-1", waiter.exchangeData());
        Assert.assertEquals(WaitDisposition.Kind.PROCEED,
            afterRestart.arrive(waiter("w-2", pending("step-b", "o-1"))).kind());
        Assert.assertEquals("the flow still to complete releases the waiter that survived",
            List.of("w-1"), afterRestart.complete("step-c", "o-1", LATER).stream().map(Waiter::id).toList());
    }

    @Test
    public void givenAWaiterWasReleasedButNotResumed_thenItIsFoundAgainAfterTheRestart() {
        repository.arrive(waiter("w-1", pending("step-b", "o-1")));
        repository.complete("step-b", "o-1", LATER);
        repository.arrive(waiter("w-2", pending("step-c", "o-1")));
        repository.fail("step-c", "o-1", LATER);

        final BarrierRepository afterRestart = new BarrierFileRepository(directory());

        Assert.assertEquals(List.of("w-1", "w-2"),
            afterRestart.findResolved().stream().map(Waiter::id).sorted().toList());
        Assert.assertEquals(Waiter.Status.FAILED, afterRestart.tryLoad("w-2").orElseThrow().status());
        Assert.assertEquals(pending("step-c", "o-1"), afterRestart.tryLoad("w-2").orElseThrow().failedBy());
    }

    @Test
    public void givenAnExpiredWaiter_thenItIsFoundAgainAfterTheRestartToBeGivenUp() {
        repository.arrive(waiter("w-1", NOW.plusSeconds(60), pending("step-b", "o-1")));
        repository.expire(NOW.plusSeconds(3600));

        final BarrierRepository afterRestart = new BarrierFileRepository(directory());

        Assert.assertEquals(Waiter.Status.EXPIRED, afterRestart.tryLoad("w-1").orElseThrow().status());
        Assert.assertEquals(List.of("w-1"), afterRestart.findResolved().stream().map(Waiter::id).toList());
        Assert.assertTrue("it is not waiting any more: a completion does not release it",
            afterRestart.complete("step-b", "o-1", LATER).isEmpty());
    }

    @Test
    public void givenAWaiterThatWasRemoved_thenItIsNotThereAfterTheRestart() throws IOException {
        repository.arrive(waiter("w-1", pending("step-b", "o-1")));
        repository.complete("step-b", "o-1", LATER);
        repository.remove("w-1");

        Assert.assertTrue(new BarrierFileRepository(directory()).tryLoad("w-1").isEmpty());
        Assert.assertEquals(0, files("waiters"));
    }

    @Test
    public void givenTheProcessDiedBetweenTheCompletionAndTheWaiter_thenTheRestartFinishesTheJob() {
        // The completion is written first; this repository dies when the waiter that it releases is to be written.
        final BarrierRepository dying = new BarrierFileRepository(directory()) {
            @Override
            protected void onWaiterStored(Waiter waiter) {
                if (waiter.status() == Waiter.Status.RELEASED) {
                    throw new IllegalStateException("the process died here");
                }
                super.onWaiterStored(waiter);
            }
        };
        dying.arrive(waiter("w-1", pending("step-b", "o-1")));
        try {
            dying.complete("step-b", "o-1", LATER);
            Assert.fail("the simulated crash must reach the caller");
        } catch (IllegalStateException expected) {
            // what a crash leaves: the completion on disk, the waiter still WAITING for it
        }

        final BarrierRepository afterRestart = new BarrierFileRepository(directory());

        Assert.assertEquals(Waiter.Status.RELEASED, afterRestart.tryLoad("w-1").orElseThrow().status());
        Assert.assertEquals(List.of("w-1"), afterRestart.findResolved().stream().map(Waiter::id).toList());
    }

    @Test
    public void givenCompletionsAreForgotten_thenTheirFilesGoToo() throws IOException {
        repository.complete("step-b", "o-1", NOW.plusSeconds(60));
        Assert.assertEquals(1, files("completions"));

        repository.purgeCompletions(NOW.plusSeconds(3600));

        Assert.assertEquals(0, files("completions"));
    }

    @Test
    public void givenIdsAndKeysWithAnyCharacter_thenEachHasItsOwnFile() throws IOException {
        for (String key : List.of("a/b", "a\b", "ordine n. 1: €", "../../etc", "x y")) {
            repository.arrive(waiter("w " + key, pending("step-b", key)));
        }

        Assert.assertEquals(5, files("waiters"));
        Assert.assertTrue(repository.tryLoad("w ../../etc").isPresent());
    }
}
