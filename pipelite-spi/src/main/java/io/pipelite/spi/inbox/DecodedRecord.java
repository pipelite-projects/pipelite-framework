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
 * The three fields a RECORD frame body decodes into, regardless of which {@link InboxFrameCodec}
 * version produced it. Package-private: purely a return type shared between the codec and {@link
 * SegmentedLogDurableInbox}, not part of any public contract.
 */
record DecodedRecord(String id, Map<String, String> metadata, byte[] payload) {
}
