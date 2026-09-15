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
package io.pipelite.spi.inbox;

/**
 * {@code EndpointURL} query-string parameter name for the per-source durable-inbox opt-out (issue
 * #70) — mirrors {@code SourceConcurrencyProperties}' own role for {@code concurrency}/{@code
 * executorType}. Resolved once at flow-build time (see {@code FlowFactory.createFlow}); default
 * on, since the whole point of #70 is a durability guarantee callers get without opting in.
 */
public final class DurableInboxProperties {

    public static final String ENABLED = "durableInbox";

    private DurableInboxProperties() {
    }

}
