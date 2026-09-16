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
package io.pipelite.channels.kafka;

import io.pipelite.dsl.definition.SourceConfigurer;
import org.apache.kafka.clients.consumer.ConsumerConfig;

import java.util.Map;

/**
 * Typed replacement for the raw {@code group.id}/{@code auto.offset.reset} query-string
 * parameters {@link KafkaConsumerService} previously read directly off {@code EndpointURL}
 * (native Kafka {@link ConsumerConfig} property names leaking straight into the URL, mixed with
 * pipelite's own {@code period}/{@code timeUnit} camelCase convention in the same string - the
 * concrete motivating example for this whole mechanism). Reuses the exact same {@link
 * ConsumerConfig} constants as keys when lowering back to query parameters in {@link
 * #contributeQueryParameters(Map)}, so {@code KafkaConsumerService#createKafkaProperties} needs no
 * changes at all - it keeps reading {@code endpointProperties.get(ConsumerConfig.GROUP_ID_CONFIG)}
 * exactly as before, unaware whether that value came from a literal query string or was
 * synthesized from this configurer.
 */
public final class KafkaSourceConfigurer extends SourceConfigurer {

    private String groupId;
    private String autoOffsetReset;

    public KafkaSourceConfigurer groupId(String groupId) {
        this.groupId = groupId;
        return this;
    }

    public KafkaSourceConfigurer autoOffsetReset(String autoOffsetReset) {
        this.autoOffsetReset = autoOffsetReset;
        return this;
    }

    @Override
    protected void contributeQueryParameters(Map<String, String> parameters) {
        if (groupId != null) {
            parameters.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        }
        if (autoOffsetReset != null) {
            parameters.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        }
    }

}
