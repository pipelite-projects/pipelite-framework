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

import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.function.BooleanSupplier;

/**
 * {@code threadCount} dedicated threads, each pulling directly off {@code consumer}'s own queue
 * and running the pipeline inline on whichever thread dequeued it — no dispatcher, no semaphore,
 * no shared pool at all. {@link EventDrivenConsumerService} uses this with {@code threadCount=1}
 * for the {@code concurrency<=1} default, which predates source concurrency entirely.
 *
 * <p>Structurally this generalizes to {@code threadCount>1} too (N threads competing directly on
 * the same queue) — benchmarked during the issue #49 dispatcher-redesign spike as a candidate for
 * {@code concurrency>1} as well, where it delivered dramatically higher throughput than {@link
 * PooledDispatchStrategy} at low-to-moderate concurrency. It was NOT adopted for that case: it
 * ties real OS thread count 1:1 to {@code threadCount} with no cap and no budget shared across
 * flows, and was measured to degrade once that oversubscribes the machine's cores — worse, once
 * summed across every concurrency>1 flow in one application, not just this one. See {@code
 * 2026-Q3-pipelite-dispatcher-redesign-spike.md} in the pipelite-framework-analysis repository for
 * the full numbers. {@link PooledDispatchStrategy} is used for {@code concurrency>1} instead.
 */
final class InlineDispatchStrategy implements DispatchStrategy {

    private final Logger logger = LoggerFactory.getLogger(InlineDispatchStrategy.class);

    private final EventDrivenConsumer consumer;
    private final ExchangeFactory exchangeFactory;
    private final ThreadFactory threadFactory;
    private final BooleanSupplier isRunAllowed;
    private final int threadCount;

    private Thread[] threads;

    InlineDispatchStrategy(EventDrivenConsumer consumer, ExchangeFactory exchangeFactory, ThreadFactory threadFactory,
                            BooleanSupplier isRunAllowed, int threadCount) {
        this.consumer = consumer;
        this.exchangeFactory = exchangeFactory;
        this.threadFactory = threadFactory;
        this.isRunAllowed = isRunAllowed;
        this.threadCount = threadCount;
    }

    @Override
    public void start() {
        if (threads == null) {
            threads = new Thread[threadCount];
            for (int i = 0; i < threadCount; i++) {
                final Thread t = threadFactory.newThread(this::runLoop);
                if (threadCount > 1) {
                    t.setName(t.getName() + "-" + i); // one-time rename at creation, not per-message
                }
                threads[i] = t;
            }
        }
        for (Thread t : threads) {
            t.start();
        }
    }

    private void runLoop() {
        while (isRunAllowed.getAsBoolean()) {
            try {
                final EventDrivenConsumer.PriorityExchange priorityExchange = consumer.takeNext();
                final Exchange exchange = priorityExchange.getExchange();
                if (consumer.isPoisonPill(exchange)) {
                    return;
                }
                try {
                    consumer.dispatchToNext(exchange);
                } catch (Throwable t) {
                    if (logger.isErrorEnabled()) {
                        logger.error("Unhandled error dispatching an Exchange inline", t);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void stop(long timeoutMillis) {
        if (threads == null) {
            return;
        }
        // One poison pill per thread: each terminates exactly one runLoop() iteration/thread.
        for (int i = 0; i < threads.length; i++) {
            consumer.consume(exchangeFactory.createExchange(EventDrivenConsumer.POISON_PILL));
        }
        for (Thread t : threads) {
            try {
                t.join(timeoutMillis);
                if (t.isAlive() && logger.isWarnEnabled()) {
                    logger.warn("Worker thread {} did not terminate within {}ms during stop()", t.getName(), timeoutMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
