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

import io.pipelite.common.support.Preconditions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.HashMap;
import java.util.Map;

public class DefaultKafkaChannelConfiguration implements KafkaChannelConfiguration {

    private static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";

    private String bootstrapServers;

    private Map<String,Object> producerConfig;
    private Map<String,Object> consumerConfig;

    @Override
    public void setBootstrapServers(String bootstrapServers) {
        Preconditions.hasText(bootstrapServers, "Illegal configuration value provided, bootstrapServers");
        this.bootstrapServers = bootstrapServers;
    }

    public String getBootstrapServers() {
        return bootstrapServers != null ? bootstrapServers : DEFAULT_BOOTSTRAP_SERVERS;
    }

    @Override
    public Map<String, Object> getProducerConfig() {
        if(producerConfig == null){
            producerConfig = createDefaultProducerConfig();
        }
        return new HashMap<>(producerConfig);
    }

    @Override
    public void setProducerConfig(Map<String, Object> producerConfig) {
        this.producerConfig = producerConfig;
    }

    @Override
    public void putProducerConfig(String key, Object value) {
        if(producerConfig == null){
            producerConfig = createDefaultProducerConfig();
        }
        producerConfig.put(key, value);
    }

    @Override
    public Map<String, Object> getConsumerConfig() {
        if(consumerConfig == null){
            consumerConfig = createDefaultConsumerConfig();
        }
        return new HashMap<>(consumerConfig);
    }

    @Override
    public void setConsumerConfig(Map<String, Object> consumerConfig) {
        this.consumerConfig = consumerConfig;
    }

    @Override
    public void putConsumerConfig(String key, Object value) {
        if(consumerConfig == null){
            consumerConfig = createDefaultConsumerConfig();
        }
        consumerConfig.put(key, value);
    }

    private static Map<String,Object> createDefaultConsumerConfig(){
        final Map<String,Object> producerConfig = new HashMap<>();
        producerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        producerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return producerConfig;
    }

    private static Map<String,Object> createDefaultProducerConfig(){
        final Map<String,Object> producerConfig = new HashMap<>();
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return producerConfig;
    }
}
