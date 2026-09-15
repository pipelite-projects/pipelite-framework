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

import java.util.Map;

/**
 * A single unacknowledged {@link DurableInbox} entry, as returned by {@link
 * DurableInbox#pendingEntries()} during startup recovery. Payload-agnostic ({@code byte[]} plus a
 * free-form metadata map) — mirrors {@code FlowExecutionDumpRepository}'s own separation from
 * {@code Exchange} serialization specifics (issue #68), so this package has no dependency on
 * {@code Exchange} itself; the caller (in {@code pipelite-core}, which does know about {@code
 * Exchange}) is responsible for deserializing {@link #getPayload()} back.
 */
public sealed interface InboxEntry permits SegmentedLogInboxEntry {

    String getId();

    byte[] getPayload();

    Map<String, String> getMetadata();

}
