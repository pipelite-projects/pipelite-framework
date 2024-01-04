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
import io.pipelite.spi.context.ContextEventListener;
import io.pipelite.spi.endpoint.DefaultProducer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.Exchange;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Map;

public class DefaultKafkaProducer extends DefaultProducer implements ContextEventListener {

    private final KafkaChannelConfiguration configuration;
    private final KafkaProducer<Object,Object> kafkaProducer;

    private final String topicName;

    public DefaultKafkaProducer(Endpoint endpoint, KafkaChannelConfiguration configuration) {

        super(endpoint);

        Preconditions.notNull(configuration, "configuration is required and cannot be null");
        this.configuration = configuration;

        final EndpointURL endpointURL = endpoint.getEndpointURL();
        topicName = endpointURL.getResource();
        kafkaProducer = new KafkaProducer<>(createKafkaProperties(configuration));
    }

    @Override
    public void process(Exchange exchange) {

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

    @Override
    public void onContextStopped() {
        if(kafkaProducer != null){
            kafkaProducer.close();
        }
    }

    private static Map<String,Object> createKafkaProperties(KafkaChannelConfiguration configuration){
        final Map<String,Object> kafkaProperties = configuration.getProducerConfig();
        kafkaProperties.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.getBootstrapServers());
        return kafkaProperties;
    }
}
