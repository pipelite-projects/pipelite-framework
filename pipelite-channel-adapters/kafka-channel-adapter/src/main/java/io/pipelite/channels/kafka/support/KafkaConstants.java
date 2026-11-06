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
package io.pipelite.channels.kafka.support;

import org.apache.kafka.clients.consumer.ConsumerConfig;

public class KafkaConstants {

    public static final String KAFKA_DEFAULT_SERIALIZER = "org.apache.kafka.common.serialization.StringSerializer";
    public static final String KAFKA_BYTE_ARRAY_SERIALIZER = "org.apache.kafka.common.serialization.ByteArraySerializer";
    public static final String KAFKA_BYTE_BUFFER_SERIALIZER = "org.apache.kafka.common.serialization.ByteBufferSerializer";
    public static final String KAFKA_BYTES_SERIALIZER = "org.apache.kafka.common.serialization.BytesSerializer";

    public static final String KAFKA_DEFAULT_DESERIALIZER = "org.apache.kafka.common.serialization.StringDeserializer";

    public static final String KAFKA_RECORD_KEY_EXCHANGE_HEADER_NAME = "org.apache.kafka.record_key";

    // What the source knows of a record besides its headers, handed over with them: the flow chooses which to keep.
    public static final String TOPIC_HEADER_NAME = "Kafka-Topic";
    public static final String PARTITION_HEADER_NAME = "Kafka-Partition";
    public static final String OFFSET_HEADER_NAME = "Kafka-Offset";
    public static final String TIMESTAMP_HEADER_NAME = "Kafka-Timestamp";

}
