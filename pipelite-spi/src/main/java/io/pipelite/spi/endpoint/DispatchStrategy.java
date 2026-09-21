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
import io.pipelite.spi.flow.exchange.FlowNode;

/**
 * Owns the lifecycle of however many threads are needed to drain an {@link EventDrivenConsumer}'s
 * queue and run its pipeline for each dequeued {@code Exchange}. {@link EventDrivenConsumerService}
 * picks an implementation based on the endpoint's configured concurrency at {@code doStart()} time
 * — the mechanism itself (how many threads, whether they run the pipeline inline or hand it off to
 * a shared pool) is entirely this interface's concern, not the service's.
 *
 * <p>Package-private: not a public extension point (yet). Two implementations exist today, {@link
 * InlineDispatchStrategy} and {@link PooledDispatchStrategy} — see their Javadoc, and {@code
 * 2026-Q3-pipelite-dispatcher-redesign-spike.md} in the pipelite-framework-analysis repository,
 * for the benchmark data behind when each is used.
 */
interface DispatchStrategy {

    /**
     * Starts this strategy's thread(s). Safe to call only once per instance — mirrors the
     * underlying {@link Thread#start()} it's built on, which cannot be called twice either.
     */
    void start();

    /**
     * Signals every thread this strategy owns to stop (however many poison pills that requires),
     * then waits up to {@code timeoutMillis} for all of them to actually terminate. A thread still
     * alive after the timeout is logged, not forcibly interrupted — mirrors the timeout-and-warn
     * behavior {@link EventDrivenConsumerService} already had before this was extracted.
     */
    void stop(long timeoutMillis);

    /**
     * Runs {@code exchange} through {@code target} (an arbitrary node in this strategy's own
     * flow — not necessarily {@code consumer}'s own head), under this same strategy's concurrency
     * accounting, instead of {@code consumer}'s queue feeding it in the usual way. Added for
     * issue #61's actual root cause (see {@code 2026-Q3-pipelite-retry-concurrency-design.md} in
     * the pipelite-framework-analysis repository): a retry resumed via {@code
     * SupplyExchangeProcessor}'s direct-jump mechanism previously ran serialized on the
     * retry-channel's own single thread, entirely outside the target flow's own {@code
     * concurrency(n)} budget — one at a time, regardless of how much of that budget was actually
     * free.
     * <p>
     * <strong>Not synchronous in general</strong> — {@link PooledDispatchStrategy} returns once
     * {@code target.process(exchange)} has been submitted to its shared pool, not once it has
     * run, so that a caller draining a batch of several pending retries (see issue #61) is never
     * itself blocked by one flow's concurrency budget being momentarily exhausted; {@link
     * InlineDispatchStrategy} has no pool to submit to and genuinely runs {@code target}
     * synchronously, which is harmless there since it is only ever used at {@code
     * concurrency<=1}, where nothing could run concurrently with it anyway.
     * <p>
     * {@code onComplete} is the way a caller learns {@code target}'s processing has actually
     * finished rather than merely started or been submitted (see issue #58: a caller like {@code
     * SupplyExchangeProcessor} must not remove a retry-channel dump until the attempt's outcome is
     * genuinely settled). It runs exactly once per call, after {@code target.process(exchange)}
     * returns or throws — on the pool thread for {@link PooledDispatchStrategy}, on the calling
     * thread for {@link InlineDispatchStrategy} — never if {@code target} is never actually
     * attempted (e.g. the dispatching thread is interrupted before submission runs).
     */
    void dispatch(FlowNode target, ExchangeImpl exchange, Runnable onComplete);
}
