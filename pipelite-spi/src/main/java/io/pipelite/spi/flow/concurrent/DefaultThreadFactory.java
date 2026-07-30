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
package io.pipelite.spi.flow.concurrent;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Names every thread it creates {@code pipelite-<role>-<identity>}, where {@code role} says what
 * kind of thread this is (e.g. {@code event}, {@code kafka}, {@code time}, {@code file}, {@code
 * pool}, {@code retry}) and {@code identity} says which one — normally the owning flow's name
 * (already unique per running application), resolved lazily via {@code identitySupplier} so the
 * factory can be built before the flow name is actually known (see {@code
 * EventDrivenConsumerService}'s constructor, and {@code TimeEndpoint}/{@code FileEndpoint}, which
 * build the factory inside {@code createConsumer()} referencing the polling consumer they just
 * constructed — {@code setFlowName(...)} on that same object always runs before {@link
 * #newThread(Runnable)} is ever actually invoked, since executors create their backing thread
 * lazily on first task submission, which happens no earlier than {@code doStart()}).
 *
 * <p>When no identity is available (the {@code identitySupplier} is {@code null}, blank, or
 * absent — the shared source-worker pool and the singleton retry-channel have no single owning
 * flow), falls back to a simple per-factory ordinal (1, 2, 3, ...) instead — stable and unique
 * within this one factory/pool, unlike the previous scheme's single {@code static} counter shared
 * across every prefix in the whole JVM.
 */
public class DefaultThreadFactory implements ThreadFactory {

    private static final String NAME_PREFIX = "pipelite";

    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final ThreadGroup threadGroup;
    private final String role;
    private final Supplier<String> identitySupplier;

    public DefaultThreadFactory(String role) {
        this(role, null);
    }

    public DefaultThreadFactory(String role, Supplier<String> identitySupplier) {
        final SecurityManager securityManager = System.getSecurityManager();
        threadGroup = securityManager != null ? securityManager.getThreadGroup()
            : Thread.currentThread().getThreadGroup();
        this.role = role;
        this.identitySupplier = identitySupplier;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        final String identity = identitySupplier != null ? identitySupplier.get() : null;
        final String name = (identity != null && !identity.isBlank())
            ? String.format("%s-%s-%s", NAME_PREFIX, role, identity)
            : String.format("%s-%s-%d", NAME_PREFIX, role, threadNumber.getAndIncrement());
        final Thread t = new Thread(threadGroup, runnable, name, 0);
        t.setUncaughtExceptionHandler(new DefaultUncaughtExceptionHandler());
        if (t.isDaemon()){
            t.setDaemon(false);
        }
        if (t.getPriority() != Thread.NORM_PRIORITY) {
            t.setPriority(Thread.NORM_PRIORITY);
        }
        return t;
    }
}
