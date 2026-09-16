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
 * Encodes/decodes the content of a {@link SegmentedLogDurableInbox} RECORD or TOMBSTONE frame
 * body for exactly one on-disk format version — {@link #version()} identifies which. {@code
 * SegmentedLogDurableInbox} itself owns prefixing every body with a 1-byte codec version before
 * handing the rest to whichever codec wrote it (see that class's own Javadoc); a codec
 * implementation only ever sees/produces its own versioned content, never the version byte itself.
 * <p>
 * One version number covers the whole record envelope (id + metadata + payload together), not
 * separate axes for "layout" and "payload encoding" — simpler to reason about on read (one byte to
 * check), and a future change to just the payload encoding is still expressed as a new overall
 * version whose codec happens to reuse the same id/metadata handling as before.
 * <p>
 * Package-private and {@code sealed}: purely an internal versioning mechanism for this class's own
 * on-disk format, never a third-party extension point — same treatment already applied to {@link
 * DurableInbox} (issue #70) and {@code io.pipelite.dsl.route.Condition} (issue #79). Old codec
 * versions stay in {@code permits} for as long as any segment on disk might still use them (never
 * for writing once a newer version exists) — retention already deletes fully-acknowledged
 * segments, so this is naturally self-limiting over time.
 */
sealed interface InboxFrameCodec permits InboxFrameCodecV1 {

    int version();

    byte[] encodeRecord(String id, Map<String, String> metadata, byte[] payload);

    DecodedRecord decodeRecord(byte[] body);

    byte[] encodeTombstone(String id);

    String decodeTombstone(byte[] body);

}
