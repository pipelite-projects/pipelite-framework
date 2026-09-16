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

import java.util.List;
import java.util.Map;

/**
 * Durably records an in-flight message from the moment a source consumer accepts it until it
 * reaches a terminal state (success, or safely handed off to a configured retry/error channel) —
 * closing the gap issue #68 left open: #68 only protects a message <em>after</em> it has already
 * failed and been routed to a retry channel, not while it's merely sitting in a consumer's
 * in-memory queue or mid-processing when the process is killed (see issue #70).
 * <p>
 * One instance is bound to exactly one source resource, resolved once at flow-build time by a
 * {@link DurableInboxProvider} — not a shared, multi-tenant store keyed by a caller-supplied
 * identifier. This keeps concurrent writers/readers per underlying file scoped to a single
 * source, and needs no resource-identifying parameter on {@link #enqueue}.
 * <p>
 * <strong>Not a third-party extension point.</strong> This is an internal framework mechanism,
 * not a public SPI meant for adapter authors to implement — {@code sealed} enforces that; only
 * the framework's own default (file-based) and future built-in implementations (e.g. a
 * Redis-backed one, issue #72) are permitted.
 */
public sealed interface DurableInbox permits SegmentedLogDurableInbox, NoOpDurableInbox {

    /**
     * Durably persists {@code payload}/{@code metadata} before returning, then returns a
     * generated id for the new entry — {@code null} only for {@link NoOpDurableInbox}, meaning
     * durability is disabled for this source (see {@link DurableInboxProperties#DURABLE_INBOX}) and the
     * caller must not expect a later {@link #acknowledge} call for this entry to mean anything.
     */
    String enqueue(byte[] payload, Map<String, String> metadata);

    /**
     * Every entry not yet {@link #acknowledge}d, in the order they were originally {@link
     * #enqueue}d. Called once, at startup, to re-inject work a crash left unacknowledged — never
     * on the hot path.
     */
    List<InboxEntry> pendingEntries();

    /**
     * Marks {@code entryId} as done — the message it durably recorded reached a terminal state
     * (success, or was hand off to a retry/error channel whose own durable record now owns it,
     * see {@code RetryChannelExceptionHandler}). Safe to call more than once for the same id.
     */
    void acknowledge(String entryId);

}
