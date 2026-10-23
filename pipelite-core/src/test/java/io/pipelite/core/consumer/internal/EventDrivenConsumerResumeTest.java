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
import io.pipelite.core.flow.internal.FlowNode;
import io.pipelite.core.context.internal.IOKeys;
import io.pipelite.core.flow.execution.inbox.DurableInbox;
import io.pipelite.core.flow.execution.inbox.SegmentedLogDurableInboxProvider;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The resume of an exchange at a node of the flow (issue #150) is run by the workers of the flow, never by whoever
 * asked for it, and waits its turn like every exchange the flow receives.
 */
public class EventDrivenConsumerResumeTest {

    /** A node that records which thread ran it and in what order, and can hold the worker until told to go on. */
    private static final class RecordingNode extends AbstractFlowNode {

        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final List<String> threads = Collections.synchronizedList(new ArrayList<>());
        volatile CountDownLatch holdUntil;
        volatile boolean fail;

        @Override
        public void process(ExchangeImpl exchange) {
            threads.add(Thread.currentThread().getName());
            events.add("processed " + exchange.getInputPayload());
            final CountDownLatch latch = holdUntil;
            if (latch != null) {
                try {
                    latch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            if (fail) {
                throw new IllegalStateException("the node fails");
            }
        }
    }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final ExchangeFactory exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
    private EventDrivenConsumerService subject;
    private ExecutorService pool;
    private final RecordingNode node = new RecordingNode();

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
        if (subject != null) {
            subject.stop();
        }
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Test
    public void givenAFlowWithoutConcurrency_thenTheResumeRunsOnItsDedicatedThreadAndNotOnTheCaller() {
        start("start-endpoint");
        final List<String> done = Collections.synchronizedList(new ArrayList<>());

        subject.resumeAt(node, exchangeFactory.createExchange("resumed"), () -> done.add("complete"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> done.size() == 1);
        Assert.assertEquals(1, node.threads.size());
        Assert.assertNotEquals(Thread.currentThread().getName(), node.threads.get(0));
        Assert.assertTrue(node.threads.get(0), node.threads.get(0).startsWith("pipelite-event-"));
    }

    @Test
    public void givenAFlowWithConcurrency_thenTheResumeRunsOnTheSharedWorkerPool() {
        start("start-endpoint?concurrency=2");
        final List<String> done = Collections.synchronizedList(new ArrayList<>());

        subject.resumeAt(node, exchangeFactory.createExchange("resumed"), () -> done.add("complete"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> done.size() == 1);
        Assert.assertEquals("test-worker-pool", node.threads.get(0));
    }

    @Test
    public void givenTheFlowIsBusy_thenTheResumeWaitsItsTurnBehindWhatIsAlreadyQueued() throws Exception {
        start("start-endpoint");
        node.holdUntil = new CountDownLatch(1);
        final List<String> done = Collections.synchronizedList(new ArrayList<>());

        subject.consume(exchangeFactory.createExchange("first"));
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> node.events.size() == 1);
        subject.resumeAt(node, exchangeFactory.createExchange("resumed"), () -> done.add("complete"));
        subject.consume(exchangeFactory.createExchange("last"));
        Thread.sleep(200);
        Assert.assertEquals("one at a time: nothing else runs while the worker is busy", 1, node.events.size());

        node.holdUntil.countDown();

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> node.events.size() == 3);
        Assert.assertEquals(List.of("processed first", "processed resumed", "processed last"), node.events);
        Assert.assertEquals(List.of("complete"), done);
    }

    @Test
    public void givenTheNodeFails_thenTheCallbackStillRunsAndTheFlowKeepsWorking() {
        start("start-endpoint");
        node.fail = true;
        final List<String> done = Collections.synchronizedList(new ArrayList<>());

        subject.resumeAt(node, exchangeFactory.createExchange("fails"), () -> done.add("complete"));
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> done.size() == 1);

        node.fail = false;
        subject.consume(exchangeFactory.createExchange("after"));
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> node.events.contains("processed after"));
    }

    @Test
    public void givenADurableInbox_thenAResumeIsNeitherWrittenToItNorAcknowledged() throws Exception {
        start("start-endpoint");
        final DurableInbox inbox = new SegmentedLogDurableInboxProvider(temporaryFolder.getRoot().toPath(),
            new DistributedIdentityGeneratorImpl()).forFlow("ship-order");
        subject.setDurableInbox(inbox);
        // an entry that is still pending, and that the exchange to resume still points to
        final String entryId = inbox.enqueue(new byte[]{1, 2, 3}, java.util.Map.of());
        final ExchangeImpl exchange = exchangeFactory.createExchange("resumed");
        exchange.setProperty(IOKeys.DURABLE_INBOX_ENTRY_ID_PROPERTY_NAME, entryId);
        final List<String> done = Collections.synchronizedList(new ArrayList<>());

        subject.resumeAt(node, exchange, () -> done.add("complete"));
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> done.size() == 1);

        Assert.assertEquals("nothing was written, and the entry was not acknowledged: only the entry of the test is there",
            1, inbox.pendingEntries().size());
    }

    @Test
    public void givenAnythingElseInTheQueue_thenTheFlowStillProcessesNewExchangesAsBefore() {
        start("start-endpoint");

        subject.consume(exchangeFactory.createExchange("new"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> node.events.contains("processed new"));
        Assert.assertTrue(node.threads.get(0), node.threads.get(0).startsWith("pipelite-event-"));
        Assert.assertTrue(FlowNode.class.isAssignableFrom(RecordingNode.class));
    }
}
