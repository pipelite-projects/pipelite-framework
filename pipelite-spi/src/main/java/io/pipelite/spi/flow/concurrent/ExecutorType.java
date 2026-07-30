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

/**
 * Strategy for how a {@code fromSource} worker pool executes concurrent work when
 * {@code concurrency > 1}. {@code concurrency} itself always means "maximum concurrent
 * pipeline executions in flight", never a literal thread count — {@link #BOUNDED_POOL}
 * happens to make the two coincide today, but a future virtual-thread-based strategy
 * would not.
 */
public enum ExecutorType {

    /**
     * Backed by the shared, bounded {@code ExecutorService} owned by the
     * {@code PipeliteContext} (JDK 17 baseline). The only value supported today.
     */
    BOUNDED_POOL

}
