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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Plain, immutable {@link InboxEntry} — the only in-memory representation {@link
 * SegmentedLogDurableInbox} uses for both a freshly {@link DurableInbox#enqueue}d entry and one
 * recovered via {@link DurableInbox#pendingEntries()} after a replay.
 */
final class SegmentedLogInboxEntry implements InboxEntry {

    private final String id;
    private final byte[] payload;
    private final Map<String, String> metadata;

    SegmentedLogInboxEntry(String id, byte[] payload, Map<String, String> metadata) {
        this.id = Objects.requireNonNull(id, "id is required and cannot be null");
        this.payload = Objects.requireNonNull(payload, "payload is required and cannot be null");
        this.metadata = metadata != null ? Collections.unmodifiableMap(metadata) : Collections.emptyMap();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public byte[] getPayload() {
        return payload;
    }

    @Override
    public Map<String, String> getMetadata() {
        return metadata;
    }

}
