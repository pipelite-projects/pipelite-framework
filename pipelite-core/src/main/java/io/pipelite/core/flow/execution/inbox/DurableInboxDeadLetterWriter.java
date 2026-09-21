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
package io.pipelite.core.flow.execution.inbox;

import io.pipelite.spi.inbox.InboxEntry;

/**
 * Last-resort sink for a durable-inbox {@link InboxEntry} whose payload could not be turned back
 * into an {@code Exchange} during recovery (issue #70) — most commonly a {@code serialVersionUID}
 * mismatch or a removed/renamed class after the payload's structure changed between the run that
 * wrote it and the run trying to read it back (see {@code DefaultPipeliteContext#recoverPendingInboxEntries},
 * which calls this once per failed entry rather than letting the failure propagate and abort
 * recovery for every other entry/flow).
 * <p>
 * Write-only by design: nothing in the framework reads a dead-lettered entry back automatically —
 * it exists purely so the raw bytes and metadata aren't silently lost, for a human (or a separate,
 * purpose-built tool) to inspect later. A caller of this interface must still {@code acknowledge}
 * the original entry against its {@code DurableInbox} once {@link #write} returns normally, so it
 * stops being handed back as pending on every future restart.
 * <p>
 * Pluggable — like {@code DurableInboxProvider}/{@code FlowExecutionDumpRepository} — via {@code
 * ConfigurablePipeliteContext#setDurableInboxDeadLetterWriter(DurableInboxDeadLetterWriter)}, with
 * {@link FileDurableInboxDeadLetterWriter} as the only implementation shipped so far.
 */
public interface DurableInboxDeadLetterWriter {

    /**
     * @param flowName the flow {@code entry} belongs to (the same value {@code
     *                 DurableInboxProvider#forFlow(String)} was called with)
     * @param entry        the entry whose payload failed to deserialize; {@link InboxEntry#getPayload()}
     *                     is written as-is, opaque, exactly as it was read from the durable inbox
     * @param cause        the deserialization failure, recorded for diagnosis
     */
    void write(String flowName, InboxEntry entry, Exception cause);

}
