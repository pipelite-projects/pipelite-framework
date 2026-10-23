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

import io.pipelite.core.consumer.internal.Consumer;
import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.definition.builder.barrier.BarrierSpecBuilder;
import io.pipelite.core.exchange.internal.DistributedIdentityGeneratorImpl;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.flow.internal.AbstractFlowNode;
import io.pipelite.core.flow.internal.Flow;
import io.pipelite.core.flow.internal.FlowNode;
import io.pipelite.dsl.barrier.BarrierConfigurator;
import io.pipelite.spi.endpoint.Endpoint;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Issue #150: a waiter that is released or failed is resumed on its own flow, once, and forgotten only when the
 * resume has finished.
 */
public class BarrierCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");

    /** What the workers of the waiting flow are asked to process: the flow, the node and the exchange. */
    private record Dispatch(Flow flow, FlowNode target, ExchangeImpl exchange, Runnable onComplete) {
    }

    /** The head of the chain of the flow, with no behavior of its own. */
    private static final class StubConsumer extends AbstractFlowNode implements Consumer {
        @Override
        public Endpoint getEndpoint() {
            return null;
        }

        @Override
        public void consume(ExchangeImpl exchange) {
        }

        @Override
        public void process(ExchangeImpl exchange) {
            next.process(exchange);
        }
    }

    private static final class Recorder extends AbstractFlowNode {
        final List<ExchangeImpl> received = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void process(ExchangeImpl exchange) {
            received.add(exchange);
        }
    }

    private static final BarrierConfigurator TWO_FLOWS = b -> b
        .correlateBy(c -> c.headerName("order-id"))
        .await("queue://step-b")
        .await("queue://step-c")
        .timeout(Duration.ofMinutes(10));

    private ExchangeFactory exchangeFactory;
    private BarrierRepository repository;
    private InternalPipeliteContext context;
    private BarrierCoordinator coordinator;
    private BarrierNode node;
    private Flow flow;
    private final Recorder recorder = new Recorder();
    private final List<Throwable> handled = new ArrayList<>();
    private final List<Dispatch> dispatches = Collections.synchronizedList(new ArrayList<>());
    private boolean runDispatchesAtOnce = true;
    private boolean dispatchFails = false;

    @Before
    public void setup() {
        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
        repository = new BarrierInMemoryRepository();
        context = Mockito.mock(InternalPipeliteContext.class);
        Mockito.when(context.getExchangeFactory()).thenReturn(exchangeFactory);
        Mockito.when(context.getBarrierRepository()).thenReturn(repository);
        coordinator = new BarrierCoordinator(context, () -> NOW, (flowOf, target, exchange, onComplete) -> {
            if (dispatchFails) {
                throw new IllegalStateException("the pool refuses it");
            }
            dispatches.add(new Dispatch(flowOf, target, exchange, onComplete));
            if (runDispatchesAtOnce) {
                try {
                    target.process(exchange);
                } finally {
                    onComplete.run();
                }
            }
        });
        Mockito.when(context.getBarrierCoordinator()).thenReturn(coordinator);

        final BarrierSpecBuilder builder = new BarrierSpecBuilder();
        TWO_FLOWS.configure(builder);
        node = new BarrierNode(builder.build(), () -> NOW);
        node.setFlowName("ship-order");
        node.setProcessorName("preparation");
        node.setPipeliteContext(context);
        node.setNext(recorder);
        node.setExceptionHandler((exception, exchange) -> handled.add(exception));
        final StubConsumer consumer = new StubConsumer();
        consumer.setNext(node);
        flow = new Flow("ship-order", "a-in", consumer);
        Mockito.when(context.tryFindFlowByName("ship-order")).thenReturn(Optional.of(flow));
    }

    private ExchangeImpl waitFor(String orderId) {
        final ExchangeImpl exchange = exchangeFactory.createExchange("payload-of-" + orderId);
        exchange.putHeader("order-id", orderId);
        node.process(exchange);
        return exchange;
    }

    @Test
    public void givenTheBarriersRegistered_thenItKnowsWhichFlowsAreAwaitedAndForHowLong() {
        Assert.assertTrue(coordinator.isAwaited("step-b"));
        Assert.assertTrue(coordinator.isAwaited("step-c"));
        Assert.assertFalse(coordinator.isAwaited("step-d"));
        Assert.assertEquals(Duration.ofMinutes(10), coordinator.retention("step-b"));
        Assert.assertEquals(Duration.ZERO, coordinator.retention("step-d"));
    }

    @Test
    public void givenTheLongestWaitOfSeveralBarriers_thenThatIsHowLongACompletionIsRemembered() {
        final BarrierSpecBuilder builder = new BarrierSpecBuilder();
        builder.await("queue://step-b", c -> c.headerName("k")).timeout(Duration.ofHours(3));
        coordinator.register("other/barrier", builder.build());

        Assert.assertEquals(Duration.ofHours(3), coordinator.retention("step-b"));
        Assert.assertEquals(Duration.ofMinutes(10), coordinator.retention("step-c"));
    }

    @Test
    public void givenTheLastFlowCompletes_thenTheExchangeIsResumedOnItsFlowAndTheWaitIsForgotten() {
        final ExchangeImpl exchange = waitFor("o-1");

        coordinator.flowCompleted("step-b", "o-1");
        Assert.assertTrue("one flow of two is not enough", dispatches.isEmpty());
        coordinator.flowCompleted("step-c", "o-1");

        Assert.assertEquals(1, dispatches.size());
        Assert.assertSame("it is the workers of the waiting flow that run it", flow, dispatches.get(0).flow());
        Assert.assertEquals("preparation", dispatches.get(0).target().getProcessorName());
        Assert.assertEquals(1, recorder.received.size());
        Assert.assertEquals("payload-of-o-1", recorder.received.get(0).getInputPayload());
        Assert.assertEquals("o-1", recorder.received.get(0).tryGetHeader("order-id").orElseThrow());
        Assert.assertTrue(repository.tryLoad("ship-order/preparation#" + exchange.getInput().getId()).isEmpty());
    }

    @Test
    public void givenAnotherKeyCompletes_thenNothingIsResumed() {
        waitFor("o-1");

        coordinator.flowCompleted("step-b", "o-2");
        coordinator.flowCompleted("step-c", "o-2");

        Assert.assertTrue(dispatches.isEmpty());
    }

    @Test
    public void givenAFlowFails_thenTheErrorHandlingOfTheWaitingFlowGetsTheFailure() {
        final ExchangeImpl exchange = waitFor("o-1");

        coordinator.flowFailed("step-c", "o-1");

        Assert.assertEquals(1, handled.size());
        final BarrierFailedException failure = (BarrierFailedException) handled.get(0);
        Assert.assertEquals(new Pending("step-c", "o-1"), failure.getFailedBy());
        Assert.assertTrue(recorder.received.isEmpty());
        Assert.assertTrue(repository.tryLoad("ship-order/preparation#" + exchange.getInput().getId()).isEmpty());
    }

    @Test
    public void givenTheProcessDiedBeforeTheResume_thenTheRecoveryResumesWhatWasReleasedAndWhatFailed() {
        final ExchangeImpl released = waitFor("o-1");
        final ExchangeImpl failed = waitFor("o-2");
        // what a crash leaves: resolved in the repository, never resumed
        repository.complete("step-b", "o-1", NOW.plusSeconds(600));
        repository.complete("step-c", "o-1", NOW.plusSeconds(600));
        repository.fail("step-b", "o-2", NOW.plusSeconds(600));

        coordinator.recover();

        Assert.assertEquals(2, dispatches.size());
        Assert.assertEquals(1, recorder.received.size());
        Assert.assertEquals("payload-of-o-1", recorder.received.get(0).getInputPayload());
        Assert.assertEquals(1, handled.size());
        Assert.assertTrue(repository.findResolved().isEmpty());
        Assert.assertNotNull(released);
        Assert.assertNotNull(failed);
    }

    @Test
    public void givenAResumeStillRunning_thenARecoveryDoesNotResumeItAgain() {
        runDispatchesAtOnce = false;
        final ExchangeImpl exchange = waitFor("o-1");
        coordinator.flowCompleted("step-b", "o-1");
        coordinator.flowCompleted("step-c", "o-1");
        Assert.assertEquals(1, dispatches.size());

        coordinator.recover();
        coordinator.recover();

        Assert.assertEquals("the resume in flight is not resumed a second time", 1, dispatches.size());
        Assert.assertTrue("and the wait is still there until it finishes",
            repository.tryLoad("ship-order/preparation#" + exchange.getInput().getId()).isPresent());

        dispatches.get(0).onComplete().run();
        Assert.assertTrue(repository.tryLoad("ship-order/preparation#" + exchange.getInput().getId()).isEmpty());
    }

    @Test
    public void givenTheFlowIsGone_thenTheWaitStaysForAContextThatHasItAgain() {
        final ExchangeImpl exchange = waitFor("o-1");
        Mockito.when(context.tryFindFlowByName("ship-order")).thenReturn(Optional.empty());

        coordinator.flowCompleted("step-b", "o-1");
        coordinator.flowCompleted("step-c", "o-1");

        Assert.assertTrue(dispatches.isEmpty());
        Assert.assertEquals(Waiter.Status.RELEASED,
            repository.tryLoad("ship-order/preparation#" + exchange.getInput().getId()).orElseThrow().status());
    }

    @Test
    public void givenTheWorkersRefuseTheResume_thenTheWaitStaysResolvedAndTheRecoveryTriesAgain() {
        final ExchangeImpl exchange = waitFor("o-1");
        dispatchFails = true;
        coordinator.flowCompleted("step-b", "o-1");
        coordinator.flowCompleted("step-c", "o-1");
        Assert.assertEquals(1, repository.findResolved().size());

        dispatchFails = false;
        coordinator.recover();

        Assert.assertEquals(1, recorder.received.size());
        Assert.assertTrue(repository.findResolved().isEmpty());
        Assert.assertNotNull(exchange);
    }
}
