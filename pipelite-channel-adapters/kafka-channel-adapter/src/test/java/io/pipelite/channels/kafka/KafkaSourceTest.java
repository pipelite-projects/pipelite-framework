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

import io.pipelite.channels.kafka.support.KafkaConstants;
import io.pipelite.dsl.Exchange;
import io.pipelite.spi.source.SourceContext;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the Kafka source does with a poll batch (issues #71, revisiting #64) and with its consumer.
 * Hands every record to the flow with {@code accept}, in order, and commits the offsets of the batch once
 * all of it has been accepted; a record that cannot be accepted (the durable inbox cannot be written)
 * keeps the whole batch uncommitted, so it is redelivered. That {@code accept} returns when the exchange is
 * durably queued, not when the flow has processed it, is the framework's side of the same property: see
 * {@code EventSourceServiceTest}. Since issue #159 the thread that polls also closes the Kafka consumer
 * when it ends. Exercised without a broker, through the package-private test seams.
 */
public class KafkaSourceTest {

    private static final String TOPIC = "test-topic";

    private Consumer<Object, Object> mockKafkaConsumer;
    private KafkaSource source;

    /** What the framework gives the source, reduced to recording what is handed over, or refusing it. */
    private static final class RecordingContext implements SourceContext {
        private final List<Exchange> accepted = new ArrayList<>();
        private final int failAtZeroBasedIndex;
        private final AtomicBoolean running = new AtomicBoolean(true);

        RecordingContext(int failAtZeroBasedIndex) {
            this.failAtZeroBasedIndex = failAtZeroBasedIndex;
        }

        @Override
        public String flowName() {
            return "test-flow";
        }

        @Override
        public Exchange newExchange(Object payload) {
            final StubExchange exchange = new StubExchange();
            exchange.setInputPayload(payload);
            return exchange;
        }

        @Override
        public void accept(Exchange exchange) {
            accepted.add(exchange);
            if (accepted.size() - 1 == failAtZeroBasedIndex) {
                throw new IllegalStateException("simulated failure to accept the record");
            }
        }

        @Override
        public java.util.concurrent.ThreadFactory threadFactory() {
            return runnable -> new Thread(runnable, "kafka-source-test");
        }

        @Override
        public boolean isRunning() {
            return running.get();
        }
    }

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        mockKafkaConsumer = mock(Consumer.class);
        source = new KafkaSource(mockKafkaConsumer, TOPIC, Duration.ofMillis(100));
    }

    private static ConsumerRecords<Object, Object> batchOf(int count) {
        final List<ConsumerRecord<Object, Object>> records = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new ConsumerRecord<>(TOPIC, 0, i, "key-" + i, "value-" + i));
        }
        final Map<TopicPartition, List<ConsumerRecord<Object, Object>>> byPartition =
            Collections.singletonMap(new TopicPartition(TOPIC, 0), records);
        return new ConsumerRecords<>(byPartition);
    }

    private RecordingContext startedWith(int failAtZeroBasedIndex) {
        final RecordingContext context = new RecordingContext(failAtZeroBasedIndex);
        // processBatch only needs the context: the polling thread is not started
        source.setContextForTest(context);
        return context;
    }

    @Test
    public void shouldHandOverEveryRecordAndCommitOnceForTheWholeBatch() {
        final RecordingContext context = startedWith(-1);

        source.processBatch(batchOf(5));

        Assert.assertEquals(5, context.accepted.size());
        verify(mockKafkaConsumer, times(1)).commitSync();
    }

    @Test
    public void shouldSkipCommitWhenARecordCannotBeAccepted() {
        // The one failure that must withhold the commit: the record could not be durably written to the
        // inbox (see the companion design doc's durability tradeoff).
        startedWith(1);

        source.processBatch(batchOf(3));

        verify(mockKafkaConsumer, never()).commitSync();
    }

    @Test
    public void shouldCommitTriviallyWhenProcessBatchIsCalledDirectlyWithZeroRecords() {
        // processBatch(...) itself has no empty-batch special case - it's the poll loop that avoids ever
        // calling it on an empty poll (via records.isEmpty()), to skip a needless commitSync() round-trip
        // on every idle poll cycle. Documented here as a characterization test so a future change to
        // processBatch(...) alone doesn't silently start committing on empty batches without the loop's guard.
        final RecordingContext context = startedWith(-1);

        source.processBatch(batchOf(0));

        Assert.assertEquals(0, context.accepted.size());
        verify(mockKafkaConsumer, times(1)).commitSync();
    }

    @Test
    public void shouldHandOverTheRecordValueAsThePayloadAndTheRecordKeyAsAHeaderInTheOrderOfTheBatch() {
        final RecordingContext context = startedWith(-1);

        source.processBatch(batchOf(3));

        Assert.assertEquals(3, context.accepted.size());
        for (int i = 0; i < 3; i++) {
            Assert.assertEquals("value-" + i, context.accepted.get(i).getInputPayload());
            Assert.assertEquals("key-" + i,
                context.accepted.get(i).tryGetHeader(KafkaConstants.KAFKA_RECORD_KEY_EXCHANGE_HEADER_NAME).orElse(null));
        }
        verify(mockKafkaConsumer, times(1)).commitSync();
    }

    @Test
    public void shouldKeepHandingOverTheRestOfTheBatchWhenOneRecordFailsButNotCommit() {
        final RecordingContext context = startedWith(1);

        source.processBatch(batchOf(4));

        Assert.assertEquals("the records after the failed one are still attempted", 4, context.accepted.size());
        verify(mockKafkaConsumer, never()).commitSync();
    }

    /**
     * Issue #159: the consumer of a source was never closed, it kept its place in the consumer group until
     * the broker timed it out. The thread that owns it closes it when the source stops.
     */
    @Test
    public void shouldCloseTheKafkaConsumerWhenTheSourceStops() throws InterruptedException {
        final CountDownLatch polling = new CountDownLatch(1);
        final CountDownLatch woken = new CountDownLatch(1);
        when(mockKafkaConsumer.poll(any(Duration.class))).thenAnswer(invocation -> {
            polling.countDown();
            woken.await(10, TimeUnit.SECONDS);
            throw new WakeupException();
        });
        doAnswer(invocation -> {
            woken.countDown();
            return null;
        }).when(mockKafkaConsumer).wakeup();
        final RecordingContext context = new RecordingContext(-1);
        source.start(context);
        Assert.assertTrue("the thread polls", polling.await(10, TimeUnit.SECONDS));

        context.running.set(false);
        source.stop();

        verify(mockKafkaConsumer, times(1)).wakeup();
        verify(mockKafkaConsumer, times(1)).close();
    }
}
