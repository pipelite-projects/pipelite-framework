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
}
