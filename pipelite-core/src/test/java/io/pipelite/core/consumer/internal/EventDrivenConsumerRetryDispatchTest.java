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
package io.pipelite.core.consumer.internal;

import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.exchange.internal.DistributedIdentityGeneratorImpl;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.flow.internal.AbstractFlowNode;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The retry of an exchange is resumed through {@link EventDrivenConsumerService#dispatchToNode} (issue #61). A flow
 * without concurrency promises one exchange at a time, and that holds for the retry too: it runs on the dedicated
 * thread of the flow in its turn, never on the thread of the caller, the retry thread (issue #168).
 */
public class EventDrivenConsumerRetryDispatchTest {

    /** A node that records which thread ran it and in what order, and can hold the worker until told to go on. */
    private static final class RecordingNode extends AbstractFlowNode {

        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final List<String> threads = Collections.synchronizedList(new ArrayList<>());
        volatile CountDownLatch holdUntil;
        volatile int running;
        volatile int maxRunning;

        @Override
        public void process(ExchangeImpl exchange) {
            synchronized (this) {
                running++;
                maxRunning = Math.max(maxRunning, running);
            }
            threads.add(Thread.currentThread().getName());
            events.add("started " + exchange.getInputPayload());
            final CountDownLatch latch = holdUntil;
            if (latch != null && "first".equals(exchange.getInputPayload())) {
                try {
                    latch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            events.add("finished " + exchange.getInputPayload());
            synchronized (this) {
                running--;
            }
        }
    }

    private final ExchangeFactory exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
    private final RecordingNode node = new RecordingNode();
    private EventDrivenConsumerService subject;
    private ExecutorService pool;

    private void start(String url) {
        final Endpoint endpoint = (Endpoint) () -> EndpointURL.parse(url);
        subject = new EventDrivenConsumerService(new EventDrivenConsumer(endpoint));
        subject.setFlowName("ship-order");
        subject.setProcessorName("ship-order-source");
        subject.setExchangeFactory(exchangeFactory);
        pool = Executors.newFixedThreadPool(2, runnable -> new Thread(runnable, "test-worker-pool"));
        subject.setSourceWorkerPool(pool);
        subject.setNext(node);
        subject.start();
    }

    @After
    public void tearDown() {
        if (node.holdUntil != null) {
            node.holdUntil.countDown();
        }
        if (subject != null) {
            subject.stop();
        }
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Test
    public void givenAFlowWithoutConcurrencyBusyWithAnotherExchange_thenTheRetryWaitsForItsTurnInsteadOfOverlappingIt() {
        start("start-endpoint");
        node.holdUntil = new CountDownLatch(1);
        final List<String> done = Collections.synchronizedList(new ArrayList<>());

        subject.consume(exchangeFactory.createExchange("first"));
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> node.events.contains("started first"));

        subject.dispatchToNode(node, exchangeFactory.createExchange("retry"), () -> done.add("complete"));

        // the caller was not held, and the retry did not start next to the exchange the flow is busy with
        Assert.assertEquals(List.of("started first"), node.events);
        Assert.assertTrue(done.isEmpty());

        node.holdUntil.countDown();

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> done.size() == 1);
        Assert.assertEquals(List.of("started first", "finished first", "started retry", "finished retry"), node.events);
        Assert.assertEquals("never two at once", 1, node.maxRunning);
    }

    @Test
    public void givenAFlowWithoutConcurrency_thenTheRetryRunsOnTheDedicatedThreadAndTheCallerDoesNotWait() {
        start("start-endpoint");
        node.holdUntil = new CountDownLatch(1);
        final List<String> done = Collections.synchronizedList(new ArrayList<>());

        // the retry is the first and only exchange, and the node holds on it: dispatchToNode must return anyway
        final ExchangeImpl retry = exchangeFactory.createExchange("first");
        subject.dispatchToNode(node, retry, () -> done.add("complete"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> node.events.contains("started first"));
        Assert.assertTrue("the caller returned while the step was still running", done.isEmpty());
        Assert.assertNotEquals(Thread.currentThread().getName(), node.threads.get(0));
        Assert.assertTrue(node.threads.get(0), node.threads.get(0).startsWith("pipelite-event-"));

        node.holdUntil.countDown();

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> done.size() == 1);
        Assert.assertEquals("onComplete runs after the step has finished", "finished first", node.events.get(node.events.size() - 1));
    }

    @Test
    public void givenAFlowWithConcurrency_thenTheRetryStillRunsOnTheSharedWorkerPool() {
        start("start-endpoint?concurrency=2");
        final List<String> done = Collections.synchronizedList(new ArrayList<>());

        subject.dispatchToNode(node, exchangeFactory.createExchange("retry"), () -> done.add("complete"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> done.size() == 1);
        Assert.assertEquals("test-worker-pool", node.threads.get(0));
    }
}
