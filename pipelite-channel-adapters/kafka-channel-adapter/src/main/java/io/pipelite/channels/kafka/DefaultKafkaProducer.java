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

import io.pipelite.channels.kafka.config.KafkaChannelConfiguration;
import io.pipelite.channels.kafka.support.KafkaConstants;
import io.pipelite.common.support.Preconditions;
import io.pipelite.dsl.Exchange;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Map;

public class DefaultKafkaProducer implements Producer {

    private final KafkaChannelConfiguration configuration;
    private final KafkaProducer<Object,Object> kafkaProducer;

    private final String topicName;

    public DefaultKafkaProducer(Endpoint endpoint, KafkaChannelConfiguration configuration) {
        this(endpoint, configuration, new KafkaProducer<>(createKafkaProperties(
            Preconditions.notNull(configuration, "configuration is required and cannot be null"))));
    }

    // Test seam: the underlying Kafka producer is handed in, so a test needs no broker.
    DefaultKafkaProducer(Endpoint endpoint, KafkaChannelConfiguration configuration, KafkaProducer<Object, Object> kafkaProducer) {
        Preconditions.notNull(configuration, "configuration is required and cannot be null");
        this.configuration = configuration;

        final EndpointURL endpointURL = endpoint.getEndpointURL();
        topicName = endpointURL.getResource();
        this.kafkaProducer = kafkaProducer;
    }

    @Override
    public void produce(Exchange exchange) {

        final Object recordValue = exchange.getInputPayloadAs(Object.class);
        if(recordValue != null) {
            final Object recordKey = exchange
                .tryGetHeaderAs(KafkaConstants.KAFKA_RECORD_KEY_EXCHANGE_HEADER_NAME, Object.class)
                .orElse(null);
            final ProducerRecord<Object, Object> producerRecord = new ProducerRecord<>(topicName, recordKey, recordValue);
            kafkaProducer.send(producerRecord);
            kafkaProducer.flush();

        }

    }

    /**
     * Closes the underlying Kafka producer, which holds a connection and a sender thread. Before issue
     * #159 this was written as a context-stopped hook that nothing ever called, so the producer of a
     * flow was never closed, and neither was the one created for each exchange supplied to a
     * {@code kafka://} destination.
     */
    @Override
    public void close() {
        kafkaProducer.close();
    }

    private static Map<String,Object> createKafkaProperties(KafkaChannelConfiguration configuration){
        final Map<String,Object> kafkaProperties = configuration.getProducerConfig();
        kafkaProperties.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.getBootstrapServers());
        return kafkaProperties;
    }
}
