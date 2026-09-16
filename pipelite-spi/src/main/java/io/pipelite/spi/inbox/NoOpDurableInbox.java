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
import java.util.List;
import java.util.Map;

/**
 * The {@link DurableInbox} wired onto a source that opted out via {@link
 * DurableInboxProperties#DURABLE_INBOX}{@code =false} — makes the write-through/acknowledge hooks in
 * {@code EventDrivenConsumer}/{@code DefaultPollingConsumer} unconditional no-ops without either
 * of them needing a null check. Also what makes Kafka's incidental {@code DurableInboxAware}
 * wiring (inherited from {@code EventDrivenConsumerService}, never actually reached since {@code
 * KafkaConsumerService} never calls {@code consume()}/{@code process()}) provably inert rather
 * than a special case.
 */
public final class NoOpDurableInbox implements DurableInbox {

    public static final NoOpDurableInbox INSTANCE = new NoOpDurableInbox();

    private NoOpDurableInbox() {
    }

    @Override
    public String enqueue(byte[] payload, Map<String, String> metadata) {
        return null;
    }

    @Override
    public List<InboxEntry> pendingEntries() {
        return Collections.emptyList();
    }

    @Override
    public void acknowledge(String entryId) {
        // no-op
    }

}
