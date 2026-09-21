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

import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.Message;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Regression coverage for a real-world symptom (issue #68's file-backed retry-channel dump
 * repository): {@code ScheduledExecutorService#scheduleAtFixedRate} silently cancels every
 * future execution of a periodic task the first time it throws — no log, no propagated
 * exception, the poll loop just stops forever. A zero-I/O in-memory {@code receive()} rarely hits
 * this; a real file-backed one hits it on the first transient hiccup, and every future dump would
 * then accumulate unread, which is exactly what looked like "dumps that never decrease" before
 * this fix.
 */
public class ScheduledPollingConsumerServiceTest {

    private ScheduledExecutorService consumerPool;

    @After
    public void shutdownPool() {
        if (consumerPool != null) {
            consumerPool.shutdownNow();
        }
    }

    private static ExchangeImpl anExchange() {
        final Message message = new SimpleMessage("id");
        message.setPayload("payload");
        return new ExchangeImpl(message);
    }

    /**
     * Throws on the very first {@code receive()} call, then returns a fresh exchange on every
     * call after that - simulates a single transient poll failure.
     */
    private static class ThrowsOnceThenSucceedsPollingConsumer extends DefaultPollingConsumer {

        private final AtomicInteger receiveCallCount = new AtomicInteger(0);
        private final AtomicInteger processedCount = new AtomicInteger(0);

        ThrowsOnceThenSucceedsPollingConsumer() {
            // A short period so the test doesn't wait a full second per tick.
            super(new DefaultEndpoint(EndpointURL.parse("start-endpoint?period=20&timeUnit=MILLISECONDS")));
        }

        @Override
        public ExchangeImpl receive() {
            if (receiveCallCount.incrementAndGet() == 1) {
                throw new RuntimeException("simulated transient receive() failure");
            }
            return anExchange();
        }

        @Override
        public void process(ExchangeImpl exchange) {
            processedCount.incrementAndGet();
        }
    }

    @Test
    public void shouldKeepPollingOnScheduleAfterReceiveThrowsOnce() {

        final ThrowsOnceThenSucceedsPollingConsumer pollingConsumer = new ThrowsOnceThenSucceedsPollingConsumer();
        consumerPool = Executors.newSingleThreadScheduledExecutor();
        final ScheduledPollingConsumerService service = new ScheduledPollingConsumerService(pollingConsumer, consumerPool);

        service.start();

        // Without the fix, the first (throwing) tick would cancel every future execution of this
        // periodic task - processedCount would stay at 0 forever and this would time out.
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> pollingConsumer.processedCount.get() >= 3);

        service.stop();
    }

    /**
     * Regression coverage for the reviewer-requested cap on {@code batchSize} (PR #75): before
     * this, an arbitrarily large query-string value would make a single scheduled tick drain that
     * many items sequentially on the shared {@code consumerPool} thread before yielding back to
     * the scheduler.
     */
    @Test
    public void shouldFailFastWhenBatchSizeExceedsMax() {

        final DefaultPollingConsumer pollingConsumer = new DefaultPollingConsumer(
            new DefaultEndpoint(EndpointURL.parse("start-endpoint?batchSize=" + (ScheduledPollingConsumerService.MAX_BATCH_SIZE + 1))));
        consumerPool = Executors.newSingleThreadScheduledExecutor();
        final ScheduledPollingConsumerService service = new ScheduledPollingConsumerService(pollingConsumer, consumerPool);

        Assert.assertThrows(IllegalArgumentException.class, service::start);
    }

}
