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

import io.pipelite.core.context.internal.IOKeys;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.definition.builder.barrier.BarrierSpecBuilder;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.exchange.internal.SimpleMessage;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Issue #150: what is read from an execution when it starts, and what the barriers are told when it ends, which is
 * nothing at all for a flow that no barrier awaits, and never a failure of the flow that is followed.
 */
public class BarrierExecutionObserverTest {

    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");

    private BarrierRepository repository;
    private BarrierCoordinator coordinator;

    @Before
    public void setup() {
        repository = new BarrierInMemoryRepository();
        final InternalPipeliteContext context = Mockito.mock(InternalPipeliteContext.class);
        Mockito.when(context.getBarrierRepository()).thenReturn(repository);
        coordinator = new BarrierCoordinator(context, () -> NOW, null);
        final BarrierSpecBuilder builder = new BarrierSpecBuilder();
        builder.await("queue://step-b", c -> c.headerName("order-id"))
            .await("queue://step-c", c -> c.headerName("order-id"))
            .timeout(Duration.ofMinutes(10));
        coordinator.register("ship-order/preparation", builder.build());
        final BarrierSpecBuilder other = new BarrierSpecBuilder();
        other.await("queue://step-b", c -> c.expression("Headers['customer']")).timeout(Duration.ofMinutes(10));
        coordinator.register("invoice/wait", other.build());
    }

    private static ExchangeImpl exchange() {
        final ExchangeImpl exchange = new ExchangeImpl(new SimpleMessage("message-1"));
        exchange.setInputPayload("payload");
        exchange.putHeader("order-id", "o-1");
        exchange.putHeader("customer", "c-7");
        return exchange;
    }

    private WaitDisposition waitsFor(String flow, String key) {
        return repository.arrive(Waiter.waiting("w-" + flow + key, "b", "f", "n", "x", "base64",
            Set.of(new Pending(flow, key)), NOW, NOW.plusSeconds(60)));
    }

    @Test
    public void givenAFlowNoBarrierAwaits_thenNothingIsKeptAndNothingIsToldAtTheEnd() {
        final ExchangeImpl exchange = exchange();

        final Runnable onEnd = new BarrierExecutionObserver("step-d", coordinator).started(exchange);

        Assert.assertNull(onEnd);
        Assert.assertNull(ExecutionTracker.of(exchange));
    }

    @Test
    public void givenAFlowThatBarriersAwaitWithDifferentKeys_thenEachKeyIsReadFromTheExchangeAsItArrived() {
        final ExchangeImpl exchange = exchange();

        final Runnable onEnd = new BarrierExecutionObserver("step-b", coordinator).started(exchange);

        Assert.assertNotNull(onEnd);
        Assert.assertEquals(Set.of(new Pending("step-b", "o-1"), new Pending("step-b", "c-7")),
            ExecutionTracker.of(exchange).pendings());
    }

    @Test
    public void givenTheExecutionEndsWithNothingHandled_thenTheBarriersAreToldItCompletedForEachKey() {
        final ExchangeImpl exchange = exchange();
        final Runnable onEnd = new BarrierExecutionObserver("step-b", coordinator).started(exchange);

        onEnd.run();

        Assert.assertEquals(WaitDisposition.Kind.PROCEED, waitsFor("step-b", "o-1").kind());
        Assert.assertEquals(WaitDisposition.Kind.PROCEED, waitsFor("step-b", "c-7").kind());
    }

    @Test
    public void givenTheExecutionFailedDefinitively_thenTheBarriersAreToldItFailed() {
        final ExchangeImpl exchange = exchange();
        final Runnable onEnd = new BarrierExecutionObserver("step-b", coordinator).started(exchange);
        ExecutionTracker.markFailed(exchange);

        onEnd.run();

        Assert.assertEquals(WaitDisposition.Kind.FAIL, waitsFor("step-b", "o-1").kind());
    }

    @Test
    public void givenTheFailureWasSavedToBeRetried_thenNothingIsToldAtTheEnd() {
        final ExchangeImpl exchange = exchange();
        final Runnable onEnd = new BarrierExecutionObserver("step-b", coordinator).started(exchange);
        ExecutionTracker.markFailed(exchange);
        ExecutionTracker.markRetrying(exchange);

        onEnd.run();

        Assert.assertEquals("still waiting for the retry", WaitDisposition.Kind.HOLD, waitsFor("step-b", "o-1").kind());
    }

    @Test
    public void givenAKeyThatCannotBeReadForOneBarrier_thenTheOthersAreKeptAndTheFlowIsNotAffected() {
        final ExchangeImpl exchange = exchange();
        exchange.removeHeader("customer");

        final Runnable onEnd = new BarrierExecutionObserver("step-b", coordinator).started(exchange);

        Assert.assertEquals(Set.of(new Pending("step-b", "o-1")), ExecutionTracker.of(exchange).pendings());
        onEnd.run();
        Assert.assertEquals(WaitDisposition.Kind.PROCEED, waitsFor("step-b", "o-1").kind());
    }

    @Test
    public void givenNoKeyCanBeRead_thenNothingIsKeptAndNothingFails() {
        final ExchangeImpl exchange = new ExchangeImpl(new SimpleMessage("message-1"));

        final Runnable onEnd = new BarrierExecutionObserver("step-b", coordinator).started(exchange);

        Assert.assertNull(onEnd);
        Assert.assertNull(exchange.getProperty(IOKeys.EXECUTION_TRACKER_PROPERTY_NAME, ExecutionTracker.class));
    }

    @Test
    public void givenTheRepositoryFailsWhenTheEndIsTold_thenTheFlowThatEndedIsNotAffected() {
        final InternalPipeliteContext context = Mockito.mock(InternalPipeliteContext.class);
        Mockito.when(context.getBarrierRepository()).thenThrow(new IllegalStateException("the disk is full"));
        final BarrierCoordinator failing = new BarrierCoordinator(context, () -> NOW, null);
        final BarrierSpecBuilder builder = new BarrierSpecBuilder();
        builder.await("queue://step-b", c -> c.headerName("order-id")).timeout(Duration.ofMinutes(10));
        failing.register("a/b", builder.build());
        final Runnable onEnd = new BarrierExecutionObserver("step-b", failing).started(exchange());

        onEnd.run();   // must not throw
    }
}
