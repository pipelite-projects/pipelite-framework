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
import io.pipelite.spi.endpoint.EndpointProperties;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.PollingProperties;
import io.pipelite.spi.source.EventSource;
import io.pipelite.spi.source.SourceContext;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * The {@code kafka://<topic>} source of a flow: a thread of its own polls the topic, hands every record to
 * the flow and commits the offsets of a batch once all of it has been accepted.
 * <p>
 * Poll/hand-over/commit loop, see issue #71 (revisiting #64). {@link SourceContext#accept} returns once the
 * exchange has been durably captured in the flow's inbox (issue #70) and queued, not once its whole
 * downstream pipeline has finished, and a record is safe to commit from then on: the poll loop blocks only
 * on the fast, local durable write, not on pipeline latency. When a record cannot be accepted, the offsets
 * of the batch are not committed and it is redelivered after a restart.
 * <p>
 * The thread owns the Kafka consumer, which is not thread-safe, and closes it when it ends: a consumer
 * left open kept its place in the consumer group until the broker timed it out.
 */
final class KafkaSource implements EventSource {

    private static final long SHUTDOWN_TIMEOUT_MILLIS = 10_000L;

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    // Fully qualified: org.apache.kafka.clients.consumer.Consumer is typed as the interface (KafkaConsumer
    // implements it) rather than the concrete class on purpose: it is what lets KafkaSourceTest mock this
    // collaborator at all, since Mockito's inline mock maker cannot subclass KafkaConsumer on newer JDKs.
    private final org.apache.kafka.clients.consumer.Consumer<Object, Object> kafkaConsumer;
    private final String topicName;
    private final Duration pollDuration;

    private volatile SourceContext context;
    private Thread thread;

    KafkaSource(KafkaChannelConfiguration configuration, Endpoint endpoint) {
        Preconditions.notNull(configuration, "configuration is required and cannot be null");
        Preconditions.notNull(endpoint, "endpoint is required and cannot be null");
        final EndpointURL endpointURL = endpoint.getEndpointURL();
        this.kafkaConsumer = new KafkaConsumer<>(createKafkaProperties(configuration, endpointURL));
        this.topicName = endpointURL.getResource();
        this.pollDuration = pollDurationOf(endpoint.getProperties());
    }

    // Test seam: the Kafka consumer is handed in, so a test needs no broker.
    KafkaSource(org.apache.kafka.clients.consumer.Consumer<Object, Object> kafkaConsumer, String topicName, Duration pollDuration) {
        this.kafkaConsumer = kafkaConsumer;
        this.topicName = topicName;
        this.pollDuration = pollDuration;
    }

    @Override
    public void start(SourceContext context) {
        this.context = context;
        thread = context.threadFactory().newThread(this::pollLoop);
        thread.start();
    }

    /** Wakes the consumer if it is blocked polling, and waits for the thread to commit nothing more and close it. */
    @Override
    public void stop() {
        kafkaConsumer.wakeup();
        final Thread polling = thread;
        if (polling != null) {
            try {
                polling.join(SHUTDOWN_TIMEOUT_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void pollLoop() {
        try {
            kafkaConsumer.subscribe(Collections.singleton(topicName));
            while (context.isRunning()) {
                try {
                    final ConsumerRecords<?, ?> records = kafkaConsumer.poll(pollDuration);
                    if (!records.isEmpty()) {
                        processBatch(records);
                    }
                } catch (WakeupException wakeup) {
                    if (context.isRunning()) {
                        sysLogger.error("Unexpected wakeup of the Kafka consumer", wakeup);
                    }
                } catch (Throwable t) {
                    if (sysLogger.isErrorEnabled()) {
                        sysLogger.error("Unhandled error in the Kafka poll loop", t);
                    }
                }
            }
        } finally {
            kafkaConsumer.close();
        }
    }

    // Test seam: gives the source its context without starting the polling thread.
    void setContextForTest(SourceContext context) {
        this.context = context;
    }

    void processBatch(ConsumerRecords<?, ?> records) {
        boolean batchFullyHandled = true;
        for (ConsumerRecord<?, ?> record : records) {
            final Object recordKey = record.key();
            final Object recordValue = record.value();
            final Exchange exchange = context.newExchange(recordValue);
            exchange.putHeader(KafkaConstants.KAFKA_RECORD_KEY_EXCHANGE_HEADER_NAME, recordKey);
            try {
                context.accept(exchange);
            } catch (Throwable t) {
                batchFullyHandled = false;
                if (sysLogger.isErrorEnabled()) {
                    sysLogger.error("Unhandled error durably writing a Kafka record to the inbox - its " +
                        "offset (and any after it in this batch) will not be committed, and will be " +
                        "redelivered after a restart", t);
                }
            }
        }
        if (batchFullyHandled) {
            kafkaConsumer.commitSync();
        } else if (sysLogger.isWarnEnabled()) {
            sysLogger.warn("Skipping offset commit for this poll batch: at least one record could not " +
                "be durably written to the inbox");
        }
    }

    private static Duration pollDurationOf(EndpointProperties endpointProperties) {
        final Long period = endpointProperties.getAsLongOrDefault(PollingProperties.PERIOD, 100L);
        final String timeUnitAsText = endpointProperties.getOrDefault(PollingProperties.TIME_UNIT, TimeUnit.MILLISECONDS.name());
        return Duration.of(period, TimeUnit.valueOf(timeUnitAsText).toChronoUnit());
    }

    private static Map<String, Object> createKafkaProperties(KafkaChannelConfiguration configuration, EndpointURL endpointURL) {
        final EndpointProperties endpointProperties = endpointURL.getProperties();
        final Map<String, Object> kafkaProperties = configuration.getConsumerConfig();
        kafkaProperties.putIfAbsent(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.getBootstrapServers());
        kafkaProperties.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, endpointProperties.get(ConsumerConfig.GROUP_ID_CONFIG));
        kafkaProperties.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, endpointProperties.getOrDefault(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"));
        kafkaProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return kafkaProperties;
    }
}
