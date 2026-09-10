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
import java.util.concurrent.TimeUnit;

public class KafkaConsumerService extends EventDrivenConsumerService {

    protected static final String PERIOD_PROPERTY_NAME = "period";
    protected static final String TIME_UNIT_PROPERTY_NAME = "timeUnit";

    // Fully qualified: org.apache.kafka.clients.consumer.Consumer would otherwise collide with
    // io.pipelite.spi.endpoint.Consumer, wildcard-imported below. Typed against the interface
    // (KafkaConsumer implements it) rather than the concrete class deliberately - among other
    // things, it's what lets KafkaConsumerServiceTest mock this collaborator at all: Mockito's
    // inline mock maker cannot subclass the concrete KafkaConsumer class on newer JDKs, but
    // interface mocking (a plain dynamic proxy) is unaffected.
    private final org.apache.kafka.clients.consumer.Consumer<Object, Object> kafkaConsumer;

    private final String topicName;

    private Thread kafkaConsumerTask;

    /**
     * Fully synchronous, single-threaded poll/process/commit loop — see issue #64. Deliberately
     * does not go through {@code consume()}/{@code process()} (the enqueue path {@link
     * EventDrivenConsumerService} normally uses): those would hand the Exchange off to a separate
     * {@code DispatchStrategy} thread and return immediately, making it impossible to know when a
     * given record has actually finished the pipeline — exactly what allowed offsets to be
     * auto-committed before processing completed. Calling {@link #dispatchToNext(Exchange)}
     * directly instead runs the pipeline right here, so the commit below is only ever reached
     * once every record in the batch has genuinely finished.
     */
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
                    try {
                        final ConsumerRecords<?, ?> records = kafkaConsumer.poll(pollDuration);
                        if (!records.isEmpty()) {
                            processBatch(records);
                        }
                    } catch (Throwable t) {
                        // Mirrors the resilience discipline EventDrivenConsumerService's dispatch
                        // strategies already apply (see #49/#50): a single bad poll/commit must
                        // not silently kill this thread the way relying only on the per-record
                        // try/catch in processBatch(...) would leave poll()/commitSync() itself
                        // unguarded.
                        if (sysLogger.isErrorEnabled()) {
                            sysLogger.error("Unhandled error in the Kafka poll loop", t);
                        }
                    }
                }
            }
        }

        /**
         * Package-visible so it can be exercised directly in tests without a real or mocked
         * {@code poll()} cycle. Runs every record in {@code records} through the pipeline, in
         * order, on the calling thread, then commits the whole batch's offsets together — but
         * only if every record reached a terminal state (success, or routed to a configured
         * retry/error channel inside the pipeline itself; see {@code
         * EventDrivenConsumer#dispatchToNext}). If any record's failure had no configured
         * retry/error channel to resolve it, the entire batch's commit is skipped: on a later
         * restart, the consumer group resumes from the last successfully committed offset, so
         * that record — and everything after it in this batch — is redelivered, rather than
         * either being silently lost (today's bug) or retried indefinitely within this same run.
         */
        void processBatch(ConsumerRecords<?, ?> records) {
            boolean batchFullyHandled = true;
            for (ConsumerRecord<?, ?> record : records) {
                final Object recordKey = record.key();
                final Object recordValue = record.value();
                final Exchange exchange = exchangeFactory.createExchange(recordValue);
                exchange.putHeader(KafkaConstants.KAFKA_RECORD_KEY_EXCHANGE_HEADER_NAME, recordKey);
                try {
                    dispatchToNext(exchange);
                } catch (Throwable t) {
                    batchFullyHandled = false;
                    if (sysLogger.isErrorEnabled()) {
                        sysLogger.error("Unhandled error processing a Kafka record with no configured " +
                            "retry/error channel - its offset (and any after it in this batch) will not " +
                            "be committed, and will be redelivered after a restart", t);
                    }
                }
            }
            if (batchFullyHandled) {
                kafkaConsumer.commitSync();
            } else if (sysLogger.isWarnEnabled()) {
                sysLogger.warn("Skipping offset commit for this poll batch: at least one record had no " +
                    "configured retry/error channel to resolve its failure");
            }
        }

    }

    public KafkaConsumerService(KafkaChannelConfiguration configuration, KafkaEndpoint endpoint) {

        super(new EventDrivenConsumer(endpoint), "kafka");

        Preconditions.notNull(configuration, "configuration is required and cannot be null");
        Preconditions.notNull(endpoint, "endpoint is required and cannot be null");

        final EndpointURL endpointURL = endpoint.getEndpointURL();
        final Map<String, Object> consumerProperties = createKafkaProperties(configuration, endpointURL);

        kafkaConsumer = new KafkaConsumer<>(consumerProperties);
        topicName = endpointURL.getResource();

    }

    /**
     * Test seam: lets a same-package test supply a fake/mock {@code KafkaConsumer} directly,
     * skipping the public constructor's {@code createKafkaProperties(...)} +
     * {@code new KafkaConsumer<>(...)} — which would otherwise try to reach a real broker.
     */
    KafkaConsumerService(org.apache.kafka.clients.consumer.Consumer<Object, Object> kafkaConsumer, String topicName, Endpoint endpoint) {
        super(new EventDrivenConsumer(endpoint), "kafka");
        this.kafkaConsumer = kafkaConsumer;
        this.topicName = topicName;
    }

    @Override
    public void doStart() {

        if (kafkaConsumerTask == null) {

            final Endpoint endpoint = getEndpoint();
            final EndpointProperties endpointProperties = endpoint.getProperties();

            final Long period = endpointProperties.getAsLongOrDefault(PERIOD_PROPERTY_NAME, 100L);
            final String timeUnitAsText = endpointProperties.getOrDefault(TIME_UNIT_PROPERTY_NAME, TimeUnit.MILLISECONDS.name());
            final TimeUnit timeUnit = TimeUnit.valueOf(timeUnitAsText);

            final Duration pollDuration = Duration.of(period, timeUnit.toChronoUnit());
            kafkaConsumerTask = threadFactory.newThread(new KafkaConsumerTask(pollDuration));
        }

        kafkaConsumerTask.start();

        // Deliberately does NOT call super.doStart(): that would start an
        // EventDrivenConsumerService DispatchStrategy (a dispatcher thread over this consumer's
        // own queue) that would sit permanently idle — KafkaConsumerTask runs the pipeline
        // synchronously on its own poll thread via dispatchToNext(...) (see #64), never going
        // through consume()/process() (the enqueue path) at all.
    }

    @Override
    public void doStop() {
        super.doStop();
    }

    private static Map<String, Object> createKafkaProperties(KafkaChannelConfiguration configuration, EndpointURL endpointURL) {

        final EndpointProperties endpointProperties = endpointURL.getProperties();

        final Map<String, Object> kafkaProperties = configuration.getConsumerConfig();
        kafkaProperties.putIfAbsent(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, configuration.getBootstrapServers());
        kafkaProperties.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, endpointProperties.get(ConsumerConfig.GROUP_ID_CONFIG));
        kafkaProperties.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, endpointProperties.getOrDefault(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"));
        // Not putIfAbsent, unlike the others: manual, post-processing commit (see #64) is the only
        // correct mode now - silently letting a user-supplied config re-enable auto-commit would
        // reintroduce the exact message-loss-on-crash bug this class exists to avoid.
        kafkaProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return kafkaProperties;
    }
}
