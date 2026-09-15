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

import io.pipelite.spi.inbox.DurableInbox;

/**
 * Implemented by consumers that durably record an in-flight {@code Exchange} before enqueueing it
 * and acknowledge it once it reaches a terminal state (issue #70) — {@link EventDrivenConsumer}
 * and {@link DefaultPollingConsumer}. Mirrors {@link io.pipelite.spi.flow.concurrent.
 * SourceWorkerPoolAware}: the concrete {@code DurableInbox} instance for a given source is
 * resolved in {@code pipelite-core} (via a {@code DurableInboxProvider}, itself wired from {@code
 * PipeliteContext}) and injected through this SPI-level marker interface at flow-build time.
 */
public interface DurableInboxAware {

    void setDurableInbox(DurableInbox durableInbox);

}
