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

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exercises {@link InboxFrameCodecV1} in isolation, independently of {@link
 * SegmentedLogDurableInbox}'s own frame/segment mechanics (issue #80) — this codec owns only the
 * record/tombstone body content, never the version byte or the length/CRC frame envelope.
 */
public class InboxFrameCodecV1Test {

    private final InboxFrameCodecV1 codec = InboxFrameCodecV1.INSTANCE;

    @Test
    public void versionShouldBeOne() {
        Assert.assertEquals(1, codec.version());
    }

    @Test
    public void shouldRoundTripARecordWithMetadataAndPayload() {

        final Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("exchangeId", "order-42");
        metadata.put("flowName", "kitchen-processing-flow");
        final byte[] payload = "some-payload-bytes".getBytes(StandardCharsets.UTF_8);

        final byte[] encoded = codec.encodeRecord("entry-1", metadata, payload);
        final DecodedRecord decoded = codec.decodeRecord(encoded);

        Assert.assertEquals("entry-1", decoded.id());
        Assert.assertEquals(metadata, decoded.metadata());
        Assert.assertArrayEquals(payload, decoded.payload());
    }

    @Test
    public void shouldRoundTripARecordWithEmptyMetadata() {

        final byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        final byte[] encoded = codec.encodeRecord("entry-2", Map.of(), payload);
        final DecodedRecord decoded = codec.decodeRecord(encoded);

        Assert.assertEquals("entry-2", decoded.id());
        Assert.assertTrue(decoded.metadata().isEmpty());
        Assert.assertArrayEquals(payload, decoded.payload());
    }

    @Test
    public void shouldTreatNullMetadataAsEmptyOnEncode() {

        final byte[] payload = "payload".getBytes(StandardCharsets.UTF_8);
        final byte[] encoded = codec.encodeRecord("entry-3", null, payload);
        final DecodedRecord decoded = codec.decodeRecord(encoded);

        Assert.assertTrue(decoded.metadata().isEmpty());
    }

    @Test
    public void shouldRoundTripATombstone() {
        final byte[] encoded = codec.encodeTombstone("entry-4");
        Assert.assertEquals("entry-4", codec.decodeTombstone(encoded));
    }
}
