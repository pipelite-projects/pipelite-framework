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

import java.util.concurrent.ExecutorService;

/**
 * Implemented by {@code Consumer}s that can dispatch work to the shared, application-wide
 * source worker pool (see the {@code fromSource} {@code concurrency} parameter) instead of
 * processing inline on their own dedicated thread. Mirrors {@link
 * io.pipelite.spi.flow.exchange.ExchangeFactoryAware}: {@code pipelite-spi} has no dependency
 * on {@code pipelite-core}, so the pool itself (owned by {@code PipeliteContext}) is injected
 * through this SPI-level marker interface rather than referenced directly.
 */
public interface SourceWorkerPoolAware {

    void setSourceWorkerPool(ExecutorService sourceWorkerPool);

}
