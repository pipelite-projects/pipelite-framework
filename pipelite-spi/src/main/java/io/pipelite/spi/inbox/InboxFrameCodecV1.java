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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Version 1 of {@link InboxFrameCodec} — the exact byte layout {@code SegmentedLogDurableInbox}
 * used before its record/tombstone bodies were versioned (issue #80), moved here verbatim with no
 * behavior change: {@code id} via {@code DataOutputStream#writeUTF}, a 2-byte metadata-entry count
 * followed by UTF key/value pairs, then a 4-byte payload length and the raw payload bytes. Metadata
 * keys/values use {@code writeUTF} (max ~64KB) - fine for short operational key/values, not for the
 * payload itself, which carries an arbitrary-size serialized {@code Exchange} and is therefore
 * length-prefixed as raw bytes instead. The tombstone body is just the id as raw UTF-8 bytes.
 */
final class InboxFrameCodecV1 implements InboxFrameCodec {

    static final InboxFrameCodecV1 INSTANCE = new InboxFrameCodecV1();

    private InboxFrameCodecV1() {
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public byte[] encodeRecord(String id, Map<String, String> metadata, byte[] payload) {
        try {
            final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            final DataOutputStream out = new DataOutputStream(buffer);
            out.writeUTF(id);
            final Map<String, String> safeMetadata = metadata != null ? metadata : Map.of();
            out.writeShort(safeMetadata.size());
            for (Map.Entry<String, String> entry : safeMetadata.entrySet()) {
                out.writeUTF(entry.getKey());
                out.writeUTF(entry.getValue());
            }
            out.writeInt(payload.length);
            out.write(payload);
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode inbox record", exception);
        }
    }

    @Override
    public DecodedRecord decodeRecord(byte[] body) {
        try {
            final DataInputStream in = new DataInputStream(new ByteArrayInputStream(body));
            final String id = in.readUTF();
            final int metadataCount = in.readUnsignedShort();
            final Map<String, String> metadata = new LinkedHashMap<>();
            for (int i = 0; i < metadataCount; i++) {
                final String key = in.readUTF();
                final String value = in.readUTF();
                metadata.put(key, value);
            }
            final int payloadLength = in.readInt();
            final byte[] payload = new byte[payloadLength];
            in.readFully(payload);
            return new DecodedRecord(id, metadata, payload);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to decode inbox record", exception);
        }
    }

    @Override
    public byte[] encodeTombstone(String id) {
        return id.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String decodeTombstone(byte[] body) {
        return new String(body, StandardCharsets.UTF_8);
    }
}
