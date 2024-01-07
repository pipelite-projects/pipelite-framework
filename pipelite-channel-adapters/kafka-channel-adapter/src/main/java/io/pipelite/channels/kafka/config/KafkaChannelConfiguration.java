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
package io.pipelite.channels.kafka.config;

import java.util.Map;
import java.util.Optional;

public interface KafkaChannelConfiguration {

    void setBootstrapServers(String bootstrapServers);
    String getBootstrapServers();

    void putProducerGlobalConfig(String key, Object value);
    Map<String,Object> getProducerGlobalConfig();
    Map<String, Object> getProducerConfig(String topicName);
    void putProducerConfig(String topicName, String key, Object value);

    void putConsumerGlobalConfig(String key, Object value);

    Map<String, Object> getConsumerConfig(String topicName);

    void putConsumerConfig(String topicName, String key, Object value);

    void addPayloadMapping(PayloadMapping payloadMapping);

    Optional<PayloadMapping> resolvePayloadMapping(String topicName);

}
