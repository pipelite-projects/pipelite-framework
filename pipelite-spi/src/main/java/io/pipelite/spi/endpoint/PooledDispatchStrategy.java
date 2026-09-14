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
import io.pipelite.spi.flow.exchange.FlowNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.function.BooleanSupplier;

/**
 * {@code dispatcherCount} dispatcher threads, each draining {@code consumer}'s queue and handing
 * every Exchange off to the shared, bounded {@code pool} — gated by a semaphore sized to {@code
 * concurrency} permits, which is the real in-flight budget (how many of this flow's Exchanges may
 * be executing on the pool at once). {@code dispatcherCount} is deliberately decoupled from {@code
 * concurrency}: {@link EventDrivenConsumerService} computes it to stay within the machine's core
 * budget regardless of how high {@code concurrency} is configured, which is what keeps real OS
 * thread count bounded even with many concurrency>1 flows in one application — the property {@link
 * InlineDispatchStrategy} does not have at {@code threadCount>1}.
 *
 * <p>{@code dispatcherCount=1} reproduces the original {@code ConcurrentDispatchTask} design
 * (single dispatcher, still used up to issue #49's tier-1 overhead fix); {@code dispatcherCount>1}
 * is the tier-2 fix — removing the single-dispatcher serialization bottleneck that caused
 * non-monotonic throughput scaling. See {@code 2026-Q3-pipelite-concurrency-degradation-analysis.md}
 * and {@code 2026-Q3-pipelite-dispatcher-redesign-spike.md} in the pipelite-framework-analysis
 * repository for the benchmark data behind both.
 */
final class PooledDispatchStrategy implements DispatchStrategy {

    private final Logger logger = LoggerFactory.getLogger(PooledDispatchStrategy.class);

    private final EventDrivenConsumer consumer;
    private final ExchangeFactory exchangeFactory;
    private final ThreadFactory threadFactory;
    private final BooleanSupplier isRunAllowed;
    private final ExecutorService pool;
    private final int dispatcherCount;
    private final Semaphore semaphore;

    private Thread[] dispatchers;

    PooledDispatchStrategy(EventDrivenConsumer consumer, ExchangeFactory exchangeFactory, ThreadFactory threadFactory,
                            BooleanSupplier isRunAllowed, ExecutorService pool, int dispatcherCount, int concurrency) {
        this.consumer = consumer;
        this.exchangeFactory = exchangeFactory;
        this.threadFactory = threadFactory;
        this.isRunAllowed = isRunAllowed;
        this.pool = pool;
        this.dispatcherCount = dispatcherCount;
        this.semaphore = new Semaphore(concurrency);
    }

    @Override
    public void start() {
        if (dispatchers == null) {
            dispatchers = new Thread[dispatcherCount];
            for (int i = 0; i < dispatcherCount; i++) {
                final Thread t = threadFactory.newThread(this::runLoop);
                if (dispatcherCount > 1) {
                    t.setName(t.getName() + "-" + i); // one-time rename at creation, not per-message
                }
                dispatchers[i] = t;
            }
        }
        for (Thread t : dispatchers) {
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
                semaphore.acquire();
                // execute(), not submit(): the Future would never be read, so submit() would only
                // cost an unnecessary FutureTask allocation per message. Which flow a pool thread
                // is currently running is attributable via the MDC pipelite.flowName/
                // pipelite.exchangeId keys dispatchToNext(...) sets, instead of renaming the
                // shared pool thread itself on every single message.
                pool.execute(() -> {
                    try {
                        consumer.dispatchToNext(exchange);
                    } catch (Throwable t) {
                        if (logger.isErrorEnabled()) {
                            logger.error("Unhandled error dispatching an Exchange on the shared source worker pool", t);
                        }
                    } finally {
                        semaphore.release();
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Asynchronous: submits the whole acquire-run-release unit to {@code pool} and returns
     * immediately, without waiting for {@code target.process(exchange)} to actually run, let
     * alone finish. This is deliberate and load-bearing for issue #61's actual point — a retry
     * backlog must be able to have more than one entry genuinely in flight at once, gated only by
     * the flow's own {@code concurrency(n)} budget, not serialized one-at-a-time on the caller's
     * thread (today, always {@code RetryService}'s single thread draining a batch per tick, see
     * {@code ScheduledPollingConsumerService}). A blocking, wait-for-completion implementation
     * was tried first and rejected: it satisfied the semaphore accounting but left every retry in
     * a batch running strictly sequentially regardless of how much of the flow's budget was
     * actually free — not the throughput fix #61 exists for. The permit is acquired inside the
     * submitted task, not before submitting it, so a caller with a full batch of retries to start
     * is never itself blocked waiting for one flow's permits to free up — submission is
     * unconditional and cheap; only the actual execution waits on the semaphore, and it waits on
     * a pool thread, not the caller's.
     * <p>
     * A caller that must know when {@code target}'s processing has actually finished (e.g. to
     * safely remove a retry-channel dump only once the outcome is settled, see issue #58) cannot
     * rely on this method's return for that — {@code onComplete} is the actual completion signal,
     * run on the pool thread once {@code target.process(exchange)} returns or throws. It does not
     * run if the permit is never acquired (thread interrupted while waiting) — {@code target} was
     * never attempted in that case, so whatever {@code onComplete} guards (e.g. a dump removal)
     * must not happen either; the caller's state is left exactly as if this dispatch had not been
     * submitted, safe to retry on a later tick.
     */
    @Override
    public void dispatch(FlowNode target, Exchange exchange, Runnable onComplete) {
        pool.execute(() -> {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                target.process(exchange);
            } catch (Throwable t) {
                if (logger.isErrorEnabled()) {
                    logger.error("Unhandled error dispatching an Exchange to an explicit target node", t);
                }
            } finally {
                semaphore.release();
                onComplete.run();
            }
        });
    }

    @Override
    public void stop(long timeoutMillis) {
        if (dispatchers == null) {
            return;
        }
        // One poison pill per dispatcher thread: each terminates exactly one runLoop().
        for (int i = 0; i < dispatchers.length; i++) {
            consumer.consume(exchangeFactory.createExchange(EventDrivenConsumer.POISON_PILL));
        }
        for (Thread t : dispatchers) {
            try {
                t.join(timeoutMillis);
                if (t.isAlive() && logger.isWarnEnabled()) {
                    logger.warn("Dispatcher thread {} did not terminate within {}ms during stop()", t.getName(), timeoutMillis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Note: this only waits for THIS flow's own dispatcher thread(s). Tasks already submitted
        // to the shared source worker pool are not awaited here — that pool is owned by
        // PipeliteContext, shared across flows, and outlives the stop of any single one; its own
        // shutdown is PipeliteContext#stop()'s responsibility.
    }
}
