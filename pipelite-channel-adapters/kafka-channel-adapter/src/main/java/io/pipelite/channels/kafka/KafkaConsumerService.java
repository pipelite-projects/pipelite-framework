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
     * Poll/write-through/commit loop — see issue #71 (revisiting #64). Goes through {@code
     * process()} (the same durable write-through + enqueue path {@link EventDrivenConsumerService}
     * normally uses for every other adapter), not the pipeline itself: a record is safe to commit
     * once it has been durably captured in the {@code DurableInbox} (issue #70), not once its
     * whole downstream pipeline has finished. This restores the standard queue/{@code
     * DispatchStrategy} concurrency model for Kafka (see {@link #doStart()}) — the poll loop no
     * longer blocks on pipeline latency, only on the (fast, local) durable write.
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
         * {@code poll()} cycle. Durably writes every record in {@code records} to the source's
         * {@code DurableInbox} and enqueues it for standard dispatch (issue #71: {@link
         * EventDrivenConsumerService#process(Exchange)} — write-through then {@code queue.put}),
         * in order, on the calling (poll) thread, then commits the whole batch's offsets together
         * — but only if every record was durably captured. Downstream pipeline execution itself
         * happens later, asynchronously, on this consumer's own {@code DispatchStrategy} thread
         * (started by {@link #doStart()} like any other adapter) — a pipeline failure there no
         * longer blocks or reverts this commit, since the record is already safe in the inbox and
         * will be retried/dead-lettered via the exact same recovery path any other durable-inbox
         * entry uses on restart (see {@code DefaultPipeliteContext#recoverPendingInboxEntries}).
         * <p>
         * If a record's write-through itself fails (e.g. local disk I/O error - not a pipeline
         * failure), the entire batch's commit is skipped: on a later restart, the consumer group
         * resumes from the last successfully committed offset, so that record — and everything
         * after it in this batch — is redelivered, rather than either being silently lost (the
         * original #64 bug) or left permanently uncommitted.
         */
        void processBatch(ConsumerRecords<?, ?> records) {
            boolean batchFullyHandled = true;
            for (ConsumerRecord<?, ?> record : records) {
                final Object recordKey = record.key();
                final Object recordValue = record.value();
                final Exchange exchange = exchangeFactory.createExchange(recordValue);
                exchange.putHeader(KafkaConstants.KAFKA_RECORD_KEY_EXCHANGE_HEADER_NAME, recordKey);
                try {
                    process(exchange);
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

            final Long period = endpointProperties.getAsLongOrDefault(PollingProperties.PERIOD, 100L);
            final String timeUnitAsText = endpointProperties.getOrDefault(PollingProperties.TIME_UNIT, TimeUnit.MILLISECONDS.name());
            final TimeUnit timeUnit = TimeUnit.valueOf(timeUnitAsText);

            final Duration pollDuration = Duration.of(period, timeUnit.toChronoUnit());
            kafkaConsumerTask = threadFactory.newThread(new KafkaConsumerTask(pollDuration));
        }

        kafkaConsumerTask.start();

        // Issue #71: now calls super.doStart() like every other EventDrivenConsumerService
        // subclass. KafkaConsumerTask only writes through to the durable inbox and enqueues (see
        // processBatch(...) above) - actual pipeline execution is drained from the queue by the
        // standard DispatchStrategy this starts, exactly as for any other adapter. Always
        // InlineDispatchStrategy in practice: DefaultEndpointFactory rejects concurrency/
        // executorType params for any protocol-backed (channel-adapter) source, Kafka included,
        // so the #66 same-partition-ordering constraint is preserved structurally, not by any
        // Kafka-specific check here.
        super.doStart();
    }

    @Override
    public void doStop() {
        super.doStop();
    }

    /**
     * Deliberately reads {@code group.id}/{@code auto.offset.reset} as Kafka's own dotted {@link
     * ConsumerConfig} key names straight from the query string, alongside pipelite's own camelCase
     * {@code period}/{@code timeUnit} in the same URL - not an oversight. Kafka's config surface is
     * already its own well-known convention; reusing its exact key names here means a value copied
     * from Kafka's own docs works unchanged, and {@link KafkaSourceConfigurer} exists precisely to
     * offer a typed alternative to reading either convention as a raw string.
     */
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
