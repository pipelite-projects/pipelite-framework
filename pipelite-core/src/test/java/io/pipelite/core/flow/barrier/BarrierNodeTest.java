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

import io.pipelite.common.support.serialization.BaseEncoding;
import io.pipelite.common.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.context.internal.IOKeys;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.definition.builder.barrier.BarrierSpecBuilder;
import io.pipelite.core.exchange.internal.DistributedIdentityGeneratorImpl;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.flow.internal.AbstractFlowNode;
import io.pipelite.dsl.barrier.BarrierConfigurator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Issue #150: what the barrier node does with an exchange that arrives, in each of the four cases. */
public class BarrierNodeTest {

    private static final String BARRIER_ID = "ship-order/preparation";
    private static final Instant NOW = Instant.parse("2026-10-23T10:00:00Z");

    public record Item(String sku, int qty) implements Serializable {
    }

    /** The step after the barrier: records what reaches it. */
    private static final class Recorder extends AbstractFlowNode {

        final List<ExchangeImpl> received = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void process(ExchangeImpl exchange) {
            received.add(exchange);
        }
    }

    private static final BarrierConfigurator TWO_FLOWS_BY_ORDER = b -> b
        .correlateBy(c -> c.headerName("order-id"))
        .await("queue://step-b")
        .await("queue://step-c")
        .timeout(Duration.ofMinutes(10));

    private ExchangeFactory exchangeFactory;
    private BarrierRepository repository;
    private Recorder recorder;
    private final List<Throwable> handled = new ArrayList<>();
    private InternalPipeliteContext context;

    @Before
    public void setup() {
        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
        repository = new BarrierInMemoryRepository();
        context = Mockito.mock(InternalPipeliteContext.class);
        Mockito.when(context.getExchangeFactory()).thenReturn(exchangeFactory);
        Mockito.when(context.getBarrierRepository()).thenReturn(repository);
        Mockito.when(context.getBarrierCoordinator()).thenReturn(new BarrierCoordinator(context));
        recorder = new Recorder();
    }

    private BarrierNode node(BarrierConfigurator configurator) {
        final BarrierSpecBuilder builder = new BarrierSpecBuilder();
        configurator.configure(builder);
        final BarrierNode node = new BarrierNode(builder.build(), () -> NOW);
        node.setFlowName("ship-order");
        node.setProcessorName("preparation");
        node.setPipeliteContext(context);
        node.setNext(recorder);
        node.setExceptionHandler((exception, exchange) -> handled.add(exception));
        return node;
    }

    private ExchangeImpl order(String orderId, Object payload) {
        final ExchangeImpl exchange = exchangeFactory.createExchange(payload);
        exchange.putHeader("order-id", orderId);
        return exchange;
    }

    private String waiterIdOf(ExchangeImpl exchange) {
        return BARRIER_ID + "#" + exchange.getInput().getId();
    }

    private ExchangeImpl keptBy(Waiter waiter) {
        return new ByteArrayToObjectConverter().convert(BaseEncoding.base64().decode(waiter.exchangeData()), ExchangeImpl.class);
    }

    @Test
    public void givenNothingHasCompleted_thenTheExchangeWaitsForEachFlowAndNothingGoesOn() {
        final ExchangeImpl exchange = order("o-1", "payload");

        node(TWO_FLOWS_BY_ORDER).process(exchange);

        Assert.assertTrue(recorder.received.isEmpty());
        Assert.assertTrue(handled.isEmpty());
        final Waiter waiter = repository.tryLoad(waiterIdOf(exchange)).orElseThrow();
        Assert.assertEquals(Waiter.Status.WAITING, waiter.status());
        Assert.assertEquals(Set.of(new Pending("step-b", "o-1"), new Pending("step-c", "o-1")), waiter.pending());
        Assert.assertEquals(BARRIER_ID, waiter.barrierId());
        Assert.assertEquals("ship-order", waiter.flowName());
        Assert.assertEquals("preparation", waiter.nodeName());
        Assert.assertEquals(NOW, waiter.createdAt());
        Assert.assertEquals(NOW.plus(Duration.ofMinutes(10)), waiter.expiresAt());
    }

    @Test
    public void givenAwaitsWithKeysOfTheirOwn_thenEachPendingHasItsOwnKey() {
        final ExchangeImpl exchange = order("o-1", "payload");
        exchange.putHeader("customer", "c-7");

        node(b -> b
            .await("queue://step-b", c -> c.headerName("order-id"))
            .await("queue://step-c", c -> c.expression("Headers['customer']"))
            .timeout(Duration.ofMinutes(10))).process(exchange);

        Assert.assertEquals(Set.of(new Pending("step-b", "o-1"), new Pending("step-c", "c-7")),
            repository.tryLoad(waiterIdOf(exchange)).orElseThrow().pending());
    }

    @Test
    public void givenAnExchangeWithHeadersAndProperties_thenTheWaitKeepsAllOfThemButTheInboxEntry() {
        final ExchangeImpl exchange = order("o-1", new Item("sku-1", 3));
        exchange.putHeader("attempt", 2);
        exchange.putHeader("tags", new ArrayList<>(List.of("a", "b")));
        exchange.setReturnAddress("queue://reply");
        exchange.setProperty(IOKeys.FLOW_EXECUTION_ATTEMPT_NUMBER_PROPERTY_NAME, 3);
        exchange.setProperty(IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME, "entry-1");
        exchange.setProperty(IOKeys.DURABLE_INBOX_RESUPPLIED_ENTRY_ID_PROPERTY_NAME, "entry-0");

        node(TWO_FLOWS_BY_ORDER).process(exchange);

        final ExchangeImpl kept = keptBy(repository.tryLoad(waiterIdOf(exchange)).orElseThrow());
        Assert.assertEquals(new Item("sku-1", 3), kept.getInputPayload());
        Assert.assertEquals("o-1", kept.tryGetHeader("order-id").orElseThrow());
        Assert.assertEquals(Integer.valueOf(2), kept.tryGetHeaderAs("attempt", Integer.class).orElseThrow());
        Assert.assertEquals(List.of("a", "b"), kept.tryGetHeaderAs("tags", List.class).orElseThrow());
        Assert.assertEquals("queue://reply", kept.tryGetReturnAddress().orElseThrow());
        Assert.assertEquals(Integer.valueOf(3), kept.getProperty(IOKeys.FLOW_EXECUTION_ATTEMPT_NUMBER_PROPERTY_NAME, Integer.class));
        Assert.assertEquals(exchange.getInput().getId(), kept.getInput().getId());
        Assert.assertNull(kept.getProperty(IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME, String.class));
        Assert.assertNull(kept.getProperty(IOKeys.DURABLE_INBOX_RESUPPLIED_ENTRY_ID_PROPERTY_NAME, String.class));
        Assert.assertEquals("the live exchange keeps its inbox entry: it is acknowledged when the node returns",
            "entry-1", exchange.getProperty(IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME, String.class));
    }

    @Test
    public void givenEveryFlowHadAlreadyCompleted_thenTheExchangeGoesOnUnchangedAndNothingIsStored() {
        repository.complete("step-b", "o-1", NOW.plusSeconds(600));
        repository.complete("step-c", "o-1", NOW.plusSeconds(600));
        final ExchangeImpl exchange = order("o-1", new Item("sku-1", 3));

        node(TWO_FLOWS_BY_ORDER).process(exchange);

        Assert.assertEquals(1, recorder.received.size());
        final ExchangeImpl next = recorder.received.get(0);
        Assert.assertEquals(new Item("sku-1", 3), next.getInputPayload());
        Assert.assertEquals("o-1", next.tryGetHeader("order-id").orElseThrow());
        Assert.assertNotSame("the next node gets the exchange of the next step", exchange, next);
        Assert.assertTrue(repository.tryLoad(waiterIdOf(exchange)).isEmpty());
    }

    @Test
    public void givenAFlowHadAlreadyFailed_thenTheErrorHandlingGetsAnExceptionThatNamesIt() {
        repository.fail("step-c", "o-1", NOW.plusSeconds(600));

        node(TWO_FLOWS_BY_ORDER).process(order("o-1", "payload"));

        Assert.assertTrue(recorder.received.isEmpty());
        Assert.assertEquals(1, handled.size());
        final BarrierFailedException failure = (BarrierFailedException) handled.get(0);
        Assert.assertEquals(new Pending("step-c", "o-1"), failure.getFailedBy());
        Assert.assertEquals(BARRIER_ID, failure.getBarrierId());
        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("queue://step-c"));
    }

    @Test
    public void givenTheSameExchangeArrivesTwice_thenItIsOneWait() {
        final ExchangeImpl exchange = order("o-1", "payload");
        final BarrierNode node = node(TWO_FLOWS_BY_ORDER);

        node.process(exchange);
        node.process(exchange);

        repository.complete("step-b", "o-1", NOW.plusSeconds(600));
        Assert.assertEquals(List.of(waiterIdOf(exchange)),
            repository.complete("step-c", "o-1", NOW.plusSeconds(600)).stream().map(Waiter::id).toList());
        Assert.assertTrue(recorder.received.isEmpty());
    }

    @Test
    public void givenTheWaitWasAlreadyReleased_thenTheReplayDoesNothingAndLeavesItToTheRecovery() {
        final ExchangeImpl exchange = order("o-1", "payload");
        final BarrierNode node = node(TWO_FLOWS_BY_ORDER);
        node.process(exchange);
        repository.complete("step-b", "o-1", NOW.plusSeconds(600));
        repository.complete("step-c", "o-1", NOW.plusSeconds(600));

        node.process(exchange);

        Assert.assertTrue(recorder.received.isEmpty());
        Assert.assertTrue(handled.isEmpty());
        Assert.assertEquals(Waiter.Status.RELEASED, repository.tryLoad(waiterIdOf(exchange)).orElseThrow().status());
    }

    @Test
    public void givenAnExchangeWithoutTheKey_thenItFailsThroughTheErrorHandlingNamingTheHeader() {
        node(TWO_FLOWS_BY_ORDER).process(exchangeFactory.createExchange("payload"));

        Assert.assertEquals(1, handled.size());
        Assert.assertTrue(handled.get(0).getMessage(), handled.get(0).getMessage().contains("order-id"));
    }

    @Test
    public void givenAPayloadThatCannotBeSerialized_thenItFailsClearlyAndNothingIsStored() {
        final ExchangeImpl exchange = order("o-1", new Object());

        node(TWO_FLOWS_BY_ORDER).process(exchange);

        Assert.assertEquals(1, handled.size());
        Assert.assertTrue(handled.get(0).getMessage(), handled.get(0).getMessage().contains("cannot wait at barrier"));
        Assert.assertTrue(handled.get(0).getMessage(), handled.get(0).getMessage().contains("serializable"));
        Assert.assertTrue(repository.tryLoad(waiterIdOf(exchange)).isEmpty());
    }

    @Test
    public void givenTheWaitIsOver_thenProceedGoesOnToTheNextNodeUnchanged() {
        final BarrierNode node = node(TWO_FLOWS_BY_ORDER);
        final ExchangeImpl exchange = order("o-1", new Item("sku-1", 3));

        node.proceed(exchange);

        Assert.assertEquals(new Item("sku-1", 3), recorder.received.get(0).getInputPayload());
    }

    @Test
    public void givenAFlowFailed_thenFailHandsTheExchangeToTheErrorHandling() {
        final BarrierNode node = node(TWO_FLOWS_BY_ORDER);

        node.fail(order("o-1", "payload"), new Pending("step-b", "o-1"));

        Assert.assertEquals(1, handled.size());
        Assert.assertTrue(handled.get(0) instanceof BarrierFailedException);
        Assert.assertTrue(recorder.received.isEmpty());
    }

    @Test
    public void thenTheBarrierIsAddressedByTheFlowAndTheNameOfTheNode() {
        Assert.assertEquals(BARRIER_ID, node(TWO_FLOWS_BY_ORDER).barrierId());
    }
}
