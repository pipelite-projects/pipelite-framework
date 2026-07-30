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
package io.pipelite.spi.endpoint;

import io.pipelite.dsl.Headers;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.exchange.Message;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class EventDrivenConsumerServiceTest {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * doStop() sends a poison pill via {@code exchangeFactory.createExchange(...)} — in
     * production this is injected by {@code FlowNodeConfigurer} through {@code
     * PipeliteContext}, but these tests wire {@code EventDrivenConsumerService} by hand, so it
     * must be supplied here too. Without it, doStop()'s poison-pill task NPEs silently on its
     * own thread and the worker never terminates.
     */
    private static final ExchangeFactory TEST_EXCHANGE_FACTORY = new ExchangeFactory() {
        @Override
        public Exchange createExchange() {
            return createExchange(null, null);
        }
        @Override
        public Exchange createExchange(Headers headers) {
            return createExchange(headers, null);
        }
        @Override
        public Exchange createExchange(Headers headers, Object inputPayload) {
            final Message message = new SimpleMessage(UUID.randomUUID().toString());
            message.setPayload(inputPayload);
            return new Exchange(message, headers);
        }
        @Override
        public Exchange createExchange(Object inputPayload) {
            return createExchange(null, inputPayload);
        }
        @Override
        public Exchange copyExchange(Exchange exchange) {
            return createExchange(exchange.getHeaders(), exchange.getInputPayloadAs(Object.class));
        }
        @Override
        public Exchange nextExchange(Exchange current) {
            return copyExchange(current);
        }
    };

    private EventDrivenConsumerService subject;

    @Before
    public void setup(){
        final Endpoint endpoint = new DefaultEndpoint(EndpointURL.parse("start-endpoint"));
        final EventDrivenConsumer consumer = new EventDrivenConsumer(endpoint);
        subject = new EventDrivenConsumerService(consumer);
        subject.setFlowName("test-flow");
        subject.setProcessorName("test-processor");
        subject.setExchangeFactory(TEST_EXCHANGE_FACTORY);
    }

    @Test
    public void shouldProcessInNewThread(){

        final long parentThreadId = Thread.currentThread().getId();
        final AtomicLong consumerThreadId = new AtomicLong();
        final AtomicInteger receivedCount = new AtomicInteger(0);

        subject.setNext(new FlowNode() {

            @Override
            public void process(Exchange exchange) {
                consumerThreadId.set(Thread.currentThread().getId());
                receivedCount.incrementAndGet();
                logger.info("Received {}", exchange.getInputPayload());
            }

            @Override
            public void setFlowName(String flowName) {
            }

            @Override
            public void setSourceEndpointResource(String sourceEndpointResource) {
            }

            @Override
            public void setProcessorName(String processorName) {
            }

            @Override
            public void setNext(FlowNode next) {
            }

            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) {
            }

            @Override
            public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) {
            }
        });

        subject.start();

        int numOfMessages = 10;
        for(int i=1;i<=numOfMessages;i++){
            Message inputMessage = new SimpleMessage(String.format("Id#%s", i));
            inputMessage.setPayload(String.format("Payload#%s", i));
            final Exchange exchange = new Exchange(inputMessage);
            subject.consume(exchange);
        }

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> receivedCount.get() == numOfMessages);
        Assert.assertNotEquals(parentThreadId, consumerThreadId.get());

        // Leaving the receive thread running would leak an "EDC-"-prefixed thread for the rest
        // of the JVM's lifetime, which shouldTerminateDispatchThreadWithinTimeoutOnStop below
        // relies on NOT being the case (it scans for any live "EDC-" thread after its own stop()).
        subject.stop();
    }

    /**
     * A {@link FlowNode} with every cross-cutting method a no-op, so tests only override
     * {@link #process(Exchange)} — same shape as the anonymous implementation above, extracted
     * so the concurrency tests below don't each repeat six empty method bodies.
     */
    private abstract static class TestFlowNode implements FlowNode {
        @Override public void setFlowName(String flowName) { }
        @Override public void setSourceEndpointResource(String sourceEndpointResource) { }
        @Override public void setProcessorName(String processorName) { }
        @Override public void setNext(FlowNode next) { }
        @Override public boolean hasNext() { return false; }
        @Override public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) { }
        @Override public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) { }
    }

    private static Exchange exchange(int id) {
        final Message inputMessage = new SimpleMessage(String.format("Id#%s", id));
        inputMessage.setPayload(String.format("Payload#%s", id));
        return new Exchange(inputMessage);
    }

    @Test
    public void shouldDispatchConcurrentlyOnSharedPoolWhenConcurrencyGreaterThanOne() {

        final Endpoint endpoint = new DefaultEndpoint(EndpointURL.parse("start-endpoint?concurrency=4"));
        final EventDrivenConsumer consumer = new EventDrivenConsumer(endpoint);
        final EventDrivenConsumerService concurrentSubject = new EventDrivenConsumerService(consumer);
        concurrentSubject.setFlowName("test-flow");
        concurrentSubject.setProcessorName("test-processor");
        concurrentSubject.setExchangeFactory(TEST_EXCHANGE_FACTORY);

        final ExecutorService sharedPool = Executors.newFixedThreadPool(4);
        concurrentSubject.setSourceWorkerPool(sharedPool);

        final int numOfMessages = 20;
        final Set<Long> observedThreadIds = ConcurrentHashMap.newKeySet();
        final AtomicInteger inFlight = new AtomicInteger(0);
        final AtomicInteger maxObservedInFlight = new AtomicInteger(0);
        final AtomicInteger receivedCount = new AtomicInteger(0);

        try {
            concurrentSubject.setNext(new TestFlowNode() {
                @Override
                public void process(Exchange exchange) {
                    observedThreadIds.add(Thread.currentThread().getId());
                    final int current = inFlight.incrementAndGet();
                    maxObservedInFlight.updateAndGet(previousMax -> Math.max(previousMax, current));
                    logger.info("processing {} on thread {} (in-flight={})",
                        exchange.getInputPayload(), Thread.currentThread().getName(), current);
                    try {
                        // force overlap: if dispatch were still serial, 20 x 50ms would take ~1s;
                        // concurrent dispatch across 4 permits keeps it well under the await timeout
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        inFlight.decrementAndGet();
                        receivedCount.incrementAndGet();
                        logger.info("processed {} (total so far={})", exchange.getInputPayload(), receivedCount.get());
                    }
                }
            });

            concurrentSubject.start();
            for (int i = 1; i <= numOfMessages; i++) {
                concurrentSubject.consume(exchange(i));
            }

            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> receivedCount.get() == numOfMessages);

            Assert.assertTrue("expected more than one distinct worker thread to have processed exchanges",
                observedThreadIds.size() > 1);
            Assert.assertTrue("expected genuine overlap (max in-flight > 1)", maxObservedInFlight.get() > 1);
            Assert.assertTrue("concurrency=4 must never be exceeded, observed max in-flight " + maxObservedInFlight.get(),
                maxObservedInFlight.get() <= 4);
        } finally {
            concurrentSubject.stop();
            sharedPool.shutdownNow();
        }
    }

    @Test
    public void shouldPreserveDefaultSingleThreadBehaviorWhenConcurrencyNotConfigured() {
        // concurrency absent from the URL (subject.setup() uses a bare "start-endpoint") — the
        // pooled dispatch path must never engage; setSourceWorkerPool is deliberately never
        // called here, so any accidental submission to a null pool would surface as an NPE.
        final AtomicInteger receivedCount = new AtomicInteger(0);
        subject.setNext(new TestFlowNode() {
            @Override
            public void process(Exchange exchange) {
                receivedCount.incrementAndGet();
                logger.info("processing {} inline on thread {}", exchange.getInputPayload(), Thread.currentThread().getName());
            }
        });

        subject.start();
        subject.consume(exchange(1));

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> receivedCount.get() == 1);
        subject.stop();
    }

    @Test
    public void shouldTerminateDispatchThreadWithinTimeoutOnStop() {

        final Endpoint endpoint = new DefaultEndpoint(EndpointURL.parse("start-endpoint?concurrency=2"));
        final EventDrivenConsumer consumer = new EventDrivenConsumer(endpoint);
        final EventDrivenConsumerService concurrentSubject = new EventDrivenConsumerService(consumer);
        concurrentSubject.setFlowName("test-flow");
        concurrentSubject.setProcessorName("test-processor");
        concurrentSubject.setExchangeFactory(TEST_EXCHANGE_FACTORY);

        final ExecutorService sharedPool = Executors.newFixedThreadPool(2);
        concurrentSubject.setSourceWorkerPool(sharedPool);
        concurrentSubject.setNext(new TestFlowNode() {
            @Override
            public void process(Exchange exchange) {
                // no-op: this test only cares about the dispatch thread's own lifecycle
            }
        });

        try {
            concurrentSubject.start();
            concurrentSubject.consume(exchange(1));
            Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> hasThreadNamed("EDC-"));

            concurrentSubject.stop();

            Assert.assertFalse("no EDC-prefixed worker thread should remain alive once stop() has returned",
                hasThreadNamed("EDC-"));
        } finally {
            sharedPool.shutdownNow();
        }
    }

    private static boolean hasThreadNamed(String prefix) {
        return Thread.getAllStackTraces().keySet().stream()
            .anyMatch(t -> t.isAlive() && t.getName().startsWith(prefix));
    }

    @Test
    public void shouldReleaseDispatchPermitWhenNextThrows() {

        final Endpoint endpoint = new DefaultEndpoint(EndpointURL.parse("start-endpoint?concurrency=2"));
        final EventDrivenConsumer consumer = new EventDrivenConsumer(endpoint);
        final EventDrivenConsumerService concurrentSubject = new EventDrivenConsumerService(consumer);
        concurrentSubject.setFlowName("test-flow");
        concurrentSubject.setProcessorName("test-processor");
        concurrentSubject.setExchangeFactory(TEST_EXCHANGE_FACTORY);

        final ExecutorService sharedPool = Executors.newFixedThreadPool(2);
        concurrentSubject.setSourceWorkerPool(sharedPool);

        final int numOfMessages = 20;
        final AtomicInteger attemptCount = new AtomicInteger(0);
        final AtomicInteger receivedCount = new AtomicInteger(0);

        try {
            concurrentSubject.setNext(new TestFlowNode() {
                @Override
                public void process(Exchange exchange) {
                    receivedCount.incrementAndGet();
                    final int attempt = attemptCount.incrementAndGet();
                    final boolean willFail = attempt % 2 == 0;
                    logger.info("processing {} on thread {} (attempt #{}, willFail={})",
                        exchange.getInputPayload(), Thread.currentThread().getName(), attempt, willFail);
                    // every other exchange fails: if the dispatch semaphore permit leaked on the
                    // exception path instead of being released, only the first `concurrency` (2)
                    // exchanges would ever be dispatched and receivedCount would stall well short
                    // of numOfMessages
                    if (willFail) {
                        throw new RuntimeException("simulated processing failure");
                    }
                }
            });

            concurrentSubject.start();
            for (int i = 1; i <= numOfMessages; i++) {
                concurrentSubject.consume(exchange(i));
            }

            Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> receivedCount.get() == numOfMessages);
        } finally {
            concurrentSubject.stop();
            sharedPool.shutdownNow();
        }
    }
}
