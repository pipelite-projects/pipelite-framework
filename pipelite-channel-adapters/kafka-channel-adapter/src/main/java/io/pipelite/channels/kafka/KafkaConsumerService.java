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
import io.pipelite.channels.kafka.config.PayloadMapping;
import io.pipelite.channels.kafka.support.KafkaConstants;
import io.pipelite.channels.kafka.support.convert.json.JsonDeserializerConfig;
import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.endpoint.*;
import io.pipelite.spi.flow.exchange.Exchange;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class KafkaConsumerService extends EventDrivenConsumerService {

    protected static final String PERIOD_PROPERTY_NAME = "period";
    protected static final String TIME_UNIT_PROPERTY_NAME = "timeUnit";

    private final KafkaConsumer<Object, Object> kafkaConsumer;

    private final String topicName;

    private Thread kafkaConsumerTask;

    public final class KafkaConsumerTask implements Runnable {

        private final Logger sysLogger = LoggerFactory.getLogger(getClass());

        private final Duration pollDuration;

        public KafkaConsumerTask(Duration pollDuration) {
            this.pollDuration = pollDuration;
        }

        @Override
        public void run() {
            synchronized (this) {

                kafkaConsumer.subscribe(Collections.singleton(topicName));

                while (isRunAllowed()) {
                    final ConsumerRecords<?, ?> records = kafkaConsumer.poll(pollDuration);
                    for (ConsumerRecord<?, ?> record : records) {
                        final Object recordKey = record.key();
                        final Object recordValue = record.value();
                        final Exchange exchange = exchangeFactory.createExchange(recordValue);
                        exchange.putHeader(KafkaConstants.KAFKA_RECORD_KEY_EXCHANGE_HEADER_NAME, recordKey);
                        KafkaConsumerService.super.consume(exchange);
                    }
                }
            }
        }

    }

    public KafkaConsumerService(KafkaChannelConfiguration configuration, KafkaEndpoint endpoint) {

        super(new EventDrivenConsumer(endpoint));

        Preconditions.notNull(configuration, "configuration is required and cannot be null");
        Preconditions.notNull(endpoint, "endpoint is required and cannot be null");

        final EndpointURL endpointURL = endpoint.getEndpointURL();
        final Map<String, Object> consumerProperties = createKafkaProperties(configuration, endpointURL);

        kafkaConsumer = new KafkaConsumer<>(consumerProperties);
        topicName = endpointURL.getResource();

    }

    @Override
    public void doStart() {

        if (kafkaConsumerTask == null) {

            final Endpoint endpoint = getEndpoint();
            final EndpointProperties endpointProperties = endpoint.getProperties();

            final Long period = endpointProperties.getAsLongOrDefault(PERIOD_PROPERTY_NAME, 200L);
            final String timeUnitAsText = endpointProperties.getOrDefault(TIME_UNIT_PROPERTY_NAME, TimeUnit.MILLISECONDS.name());
            final TimeUnit timeUnit = TimeUnit.valueOf(timeUnitAsText);

            final Duration pollDuration = Duration.of(period, timeUnit.toChronoUnit());
            kafkaConsumerTask = threadFactory.newThread(new KafkaConsumerTask(pollDuration));
        }

        kafkaConsumerTask.start();

        super.doStart();

    }

    @Override
    public void doStop() {
        super.doStop();
    }

    private static Map<String, Object> createKafkaProperties(KafkaChannelConfiguration configuration, EndpointURL endpointURL) {

        final String topicName = endpointURL.getResource();
        //final EndpointProperties endpointProperties = endpointURL.getProperties();

        final Map<String, Object> kafkaProperties = configuration.getConsumerConfig(topicName);

        kafkaProperties.putIfAbsent(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.getBootstrapServers());
        //kafkaProperties.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, endpointProperties.get(ConsumerConfig.GROUP_ID_CONFIG));
        //kafkaProperties.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, endpointProperties.getOrDefault(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"));

        final Optional<PayloadMapping> payloadMappingHolder = configuration.resolvePayloadMapping(topicName);

        payloadMappingHolder.ifPresent(payloadMapping -> {

            payloadMapping.tryGetKeyDeserializer().ifPresent(keyDeserializer ->
                kafkaProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializer));

            payloadMapping.tryGetValueDeserializer().ifPresent(valueDeserializer ->
                kafkaProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializer));

            payloadMapping.tryGetValueDeserializerDefaultType().ifPresent(valueDeserializerDefaultType ->
                kafkaProperties.put(JsonDeserializerConfig.VALUE_DEFAULT_TYPE, valueDeserializerDefaultType));

        });

        return kafkaProperties;

    }

}
