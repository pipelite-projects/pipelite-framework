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

import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.definition.builder.barrier.BarrierSpec;
import io.pipelite.core.definition.builder.barrier.BarrierSpecBuilder;
import io.pipelite.core.exchange.internal.DistributedIdentityGeneratorImpl;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.flow.internal.AbstractFlowNode;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.barrier.BarrierConfigurator;
import io.pipelite.dsl.barrier.Release;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Issue #150: a barrier counts each contributor once, releases exactly one exchange when it is complete, and
 * keeps what it needs to release again if the rest of the flow did not finish.
 */
public class BarrierNodeTest {

    private record Result(String order, String step, boolean failed) {
    }

    /** The step after the barrier: records what reaches it, and can be told to fail once. */
    private static final class Recorder extends AbstractFlowNode {

        final List<ExchangeImpl> received = Collections.synchronizedList(new ArrayList<>());
        volatile RuntimeException failWith;

        @Override
        public void process(ExchangeImpl exchange) {
            final RuntimeException failure = failWith;
            if (failure != null) {
                failWith = null;
                throw failure;
            }
            received.add(exchange);
        }
    }

    private static final BarrierConfigurator BY_ORDER_COUNTING_TWO = b -> b
        .correlateBy(c -> c.payloadAttribute(Result::order))
        .expect(2)
        .contributorBy(ex -> ex.getInputPayloadAs(Result.class).step())
        .timeout(Duration.ofMinutes(10));

    private ExchangeFactory exchangeFactory;
    private BarrierRepository repository;
    private InternalPipeliteContext context;
    private Recorder recorder;

    @Before
    public void setup() {
        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
        repository = new BarrierInMemoryRepository();
        context = Mockito.mock(InternalPipeliteContext.class);
        Mockito.when(context.getExchangeFactory()).thenReturn(exchangeFactory);
        Mockito.when(context.getBarrierRepository()).thenReturn(repository);
        recorder = new Recorder();
    }

    private BarrierNode node(BarrierConfigurator configurator) {
        final BarrierSpecBuilder builder = new BarrierSpecBuilder();
        configurator.configure(builder);
        final BarrierSpec spec = builder.build();
        final BarrierNode node = new BarrierNode(spec, Instant::now);
        node.setFlowName("test-flow");
        node.setSourceEndpointResource("in");
        node.setProcessorName("barrier-step");
        node.setPipeliteContext(context);
        node.setNext(recorder);
        return node;
    }

    private ExchangeImpl arrival(String order, String step) {
        return arrival(order, step, false);
    }

    private ExchangeImpl arrival(String order, String step, boolean failed) {
        return exchangeFactory.createExchange(new Result(order, step, failed));
    }

    @Test
    public void givenAnIncompleteBarrier_thenNothingGoesOnAndTheArrivalIsKept() {
        final BarrierNode subject = node(BY_ORDER_COUNTING_TWO);

        subject.process(arrival("o-1", "record"));

        Assert.assertTrue(recorder.received.isEmpty());
        final BarrierState state = repository.tryLoad("test-flow/barrier-step", "o-1").orElseThrow();
        Assert.assertEquals(BarrierState.Status.OPEN, state.getStatus());
        Assert.assertEquals(List.of("record"), new ArrayList<>(state.contributors()));
    }

    @Test
    public void givenTheLastContributorArrives_thenExactlyOneExchangeGoesOnWithTheListOfPayloads() {
        final BarrierNode subject = node(BY_ORDER_COUNTING_TWO);

        subject.process(arrival("o-1", "record"));
        subject.process(arrival("o-1", "check"));

        Assert.assertEquals(1, recorder.received.size());
        final List<?> payloads = recorder.received.get(0).getInputPayloadAs(List.class);
        Assert.assertEquals(2, payloads.size());
        Assert.assertEquals("record", ((Result) payloads.get(0)).step());
        Assert.assertEquals("check", ((Result) payloads.get(1)).step());
        final BarrierState state = repository.tryLoad("test-flow/barrier-step", "o-1").orElseThrow();
        Assert.assertEquals(BarrierState.Status.DONE, state.getStatus());
        Assert.assertTrue("a done state keeps no contribution", state.contributions().isEmpty());
    }

    @Test
    public void givenDifferentKeys_thenEachBarrierWaitsOnItsOwn() {
        final BarrierNode subject = node(BY_ORDER_COUNTING_TWO);

        subject.process(arrival("o-1", "record"));
        subject.process(arrival("o-2", "record"));
        subject.process(arrival("o-2", "check"));

        Assert.assertEquals(1, recorder.received.size());
        Assert.assertEquals(BarrierState.Status.OPEN, repository.tryLoad("test-flow/barrier-step", "o-1").orElseThrow().getStatus());
    }

    @Test
    public void givenReleaseFirst_thenTheFirstPayloadGoesOn() {
        final BarrierNode subject = node(b -> BY_ORDER_COUNTING_TWO.configure(b).release(Release.first()));

        subject.process(arrival("o-1", "record"));
        subject.process(arrival("o-1", "check"));

        Assert.assertEquals("record", recorder.received.get(0).getInputPayloadAs(Result.class).step());
    }

    @Test
    public void givenAnAggregator_thenItsResultGoesOnAndItGetsTheExchangesInArrivalOrder() {
        final AtomicReference<List<String>> seen = new AtomicReference<>();
        final BarrierNode subject = node(b -> BY_ORDER_COUNTING_TWO.configure(b).release(Release.aggregate(exchanges -> {
            seen.set(exchanges.stream().map(e -> e.getInputPayloadAs(Result.class).step()).toList());
            return "merged-" + exchanges.size();
        })));

        subject.process(arrival("o-1", "plan"));
        subject.process(arrival("o-1", "record"));

        Assert.assertEquals(List.of("plan", "record"), seen.get());
        Assert.assertEquals("merged-2", recorder.received.get(0).getInputPayloadAs(String.class));
    }

    @Test
    public void givenTheHeadersOfTheFirstContributor_thenTheyGoOnAndNotTheOnesOfTheLast() {
        final BarrierNode subject = node(BY_ORDER_COUNTING_TWO);
        final ExchangeImpl first = arrival("o-1", "record");
        first.putHeader("origin", "first");
        final ExchangeImpl last = arrival("o-1", "check");
        last.putHeader("origin", "last");
        last.setProperty("a-property-of-the-thread", "kept");

        subject.process(first);
        subject.process(last);

        final ExchangeImpl released = recorder.received.get(0);
        Assert.assertEquals("first", released.tryGetHeader("origin").orElseThrow());
        Assert.assertEquals("kept", released.getProperty("a-property-of-the-thread", String.class));
    }

    @Test
    public void givenNamedContributors_thenTheOrderOfArrivalDoesNotMatterAndAnUnknownOneFails() {
        final BarrierNode subject = node(b -> b
            .correlateBy(c -> c.payloadAttribute(Result::order))
            .expect("record", "check", "plan")
            .contributorBy(ex -> ex.getInputPayloadAs(Result.class).step())
            .timeout(Duration.ofMinutes(10)));

        subject.process(arrival("o-1", "plan"));
        subject.process(arrival("o-1", "record"));
        Assert.assertTrue(recorder.received.isEmpty());
        subject.process(arrival("o-1", "check"));
        Assert.assertEquals(1, recorder.received.size());

        try {
            subject.process(arrival("o-2", "surprise"));
            Assert.fail("a contributor the barrier does not wait for must fail");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("surprise"));
        }
    }

    @Test
    public void givenTheSameMessageTwice_thenItCountsOnce() {
        // no contributorBy: the contributor is the message, so a replay from the inbox is the same contributor
        final BarrierNode subject = node(b -> b
            .correlateBy(c -> c.payloadAttribute(Result::order))
            .expect(2)
            .timeout(Duration.ofMinutes(10)));
        final ExchangeImpl one = arrival("o-1", "record");

        subject.process(one);
        subject.process(one);

        Assert.assertTrue("the same message does not complete a barrier of two", recorder.received.isEmpty());
        Assert.assertEquals(1, repository.tryLoad("test-flow/barrier-step", "o-1").orElseThrow().contributors().size());
        subject.process(arrival("o-1", "check"));
        Assert.assertEquals(1, recorder.received.size());
    }

    @Test
    public void givenAContributionAfterTheRelease_thenItIsIgnoredAndStartsNoNewBarrier() {
        final BarrierNode subject = node(BY_ORDER_COUNTING_TWO);
        subject.process(arrival("o-1", "record"));
        subject.process(arrival("o-1", "check"));

        subject.process(arrival("o-1", "record"));   // a replay that comes after the release

        Assert.assertEquals(1, recorder.received.size());
        Assert.assertEquals(BarrierState.Status.DONE, repository.tryLoad("test-flow/barrier-step", "o-1").orElseThrow().getStatus());
    }

    @Test
    public void givenAContributionThatSaysItFailed_thenTheErrorHandlingGetsItAndTheOthersAreIgnored() {
        final BarrierNode subject = node(b -> BY_ORDER_COUNTING_TWO.configure(b)
            .failedWhen(ex -> ex.getInputPayloadAs(Result.class).failed()));
        final List<Throwable> handled = new ArrayList<>();
        subject.setExceptionHandler((exception, exchange) -> handled.add(exception));

        subject.process(arrival("o-1", "record"));
        subject.process(arrival("o-1", "check", true));
        subject.process(arrival("o-1", "plan"));   // the others keep arriving after the failure

        Assert.assertEquals(1, handled.size());
        final BarrierFailedException failure = (BarrierFailedException) handled.get(0);
        Assert.assertEquals("check", failure.getFailedContributor());
        Assert.assertEquals(List.of("record", "check"), failure.getContributors());
        Assert.assertEquals("test-flow/barrier-step", failure.getBarrierId());
        Assert.assertTrue("nothing goes on after a failure", recorder.received.isEmpty());
        Assert.assertEquals(BarrierState.Status.DONE, repository.tryLoad("test-flow/barrier-step", "o-1").orElseThrow().getStatus());
    }

    @Test
    public void givenTheRestOfTheFlowDidNotFinish_thenTheReplayOfTheClosingArrivalReleasesAgain() {
        final BarrierNode subject = node(BY_ORDER_COUNTING_TWO);
        subject.process(arrival("o-1", "record"));
        final ExchangeImpl closing = arrival("o-1", "check");
        recorder.failWith = new IllegalStateException("the process died here");

        try {
            subject.process(closing);
            Assert.fail("the failure of the rest of the flow must reach the caller");
        } catch (IllegalStateException expected) {
            // unhandled: the inbox entry of the closing arrival stays pending
        }
        Assert.assertEquals("closed, not done", BarrierState.Status.COMPLETED,
            repository.tryLoad("test-flow/barrier-step", "o-1").orElseThrow().getStatus());

        subject.process(arrival("o-1", "record"));   // another contributor, late: ignored
        Assert.assertTrue(recorder.received.isEmpty());

        subject.process(closing);                    // the replay of the closing arrival

        Assert.assertEquals(1, recorder.received.size());
        Assert.assertEquals(2, recorder.received.get(0).getInputPayloadAs(List.class).size());
        Assert.assertEquals(BarrierState.Status.DONE, repository.tryLoad("test-flow/barrier-step", "o-1").orElseThrow().getStatus());
    }

    @Test
    public void givenManyContributorsArrivingAtOnce_thenTheBarrierIsReleasedExactlyOnce() throws Exception {
        final int contributors = 16;
        final BarrierNode subject = node(b -> b
            .correlateBy(c -> c.payloadAttribute(Result::order))
            .expect(contributors)
            .timeout(Duration.ofMinutes(10)));
        final ExecutorService pool = Executors.newFixedThreadPool(contributors);
        final CountDownLatch start = new CountDownLatch(1);
        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        try {
            for (int i = 0; i < contributors; i++) {
                final ExchangeImpl exchange = arrival("o-1", "step-" + i);
                pool.submit(() -> {
                    try {
                        start.await();
                        subject.process(exchange);
                    } catch (Throwable t) {
                        errors.add(t);
                    }
                });
            }
            start.countDown();
        } finally {
            pool.shutdown();
            Assert.assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }

        Assert.assertTrue(errors.toString(), errors.isEmpty());
        Assert.assertEquals("released exactly once", 1, recorder.received.size());
        Assert.assertEquals(contributors, recorder.received.get(0).getInputPayloadAs(List.class).size());
    }

    @Test
    public void givenAnExchangeWithoutTheCorrelation_thenItFailsThroughTheErrorHandling() {
        final BarrierNode subject = node(b -> b
            .correlateBy(c -> c.headerName("order-id"))
            .expect(2)
            .timeout(Duration.ofMinutes(10)));
        final List<Throwable> handled = new ArrayList<>();
        subject.setExceptionHandler((exception, exchange) -> handled.add(exception));

        subject.process(arrival("o-1", "record"));

        Assert.assertEquals(1, handled.size());
        Assert.assertTrue(handled.get(0).getMessage(), handled.get(0).getMessage().contains("order-id"));
    }

    @Test
    public void thenTheBarrierIsAddressedByTheFlowAndTheNameOfTheNode() {
        Assert.assertEquals("test-flow/barrier-step", node(BY_ORDER_COUNTING_TWO).barrierId());
    }
}
