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

import io.pipelite.dsl.Headers;
import io.pipelite.spi.endpoint.DefaultEndpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.exchange.Message;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;
import io.pipelite.spi.inbox.SegmentedLogDurableInbox;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Covers the #71 fix directly: {@code processBatch(...)} must durably write every record in a
 * poll batch to the source's {@code DurableInbox} (issue #70) and commit the batch's offsets once
 * that write-through has succeeded for the whole batch — never waiting for, or being undone by,
 * downstream pipeline outcome, which now runs later and asynchronously off this consumer's own
 * queue (see {@code KafkaConsumerService#doStart}). Only a failure of the durable write itself
 * (not a downstream pipeline failure) may skip the commit. Exercised without a real or mocked
 * {@code poll()} cycle via the package-private test-seam constructor and {@code
 * processBatch(...)}'s package visibility.
 */
public class KafkaConsumerServiceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final ExchangeFactory TEST_EXCHANGE_FACTORY = new ExchangeFactory() {
        @Override public ExchangeImpl createExchange() { return createExchange(null, null); }
        @Override public ExchangeImpl createExchange(Headers headers) { return createExchange(headers, null); }
        @Override public ExchangeImpl createExchange(Headers headers, Object inputPayload) {
            final Message message = new SimpleMessage(UUID.randomUUID().toString());
            message.setPayload(inputPayload);
            return new ExchangeImpl(message, headers);
        }
        @Override public ExchangeImpl createExchange(Object inputPayload) { return createExchange(null, inputPayload); }
        @Override public ExchangeImpl copyExchange(ExchangeImpl exchange) {
            return createExchange(exchange.getHeaders(), exchange.getInputPayloadAs(Object.class));
        }
        @Override public ExchangeImpl nextExchange(ExchangeImpl current) { return copyExchange(current); }
    };

    private static final String TOPIC = "test-topic";

    private Consumer<Object, Object> mockKafkaConsumer;
    private KafkaConsumerService service;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        mockKafkaConsumer = mock(Consumer.class);
        service = new KafkaConsumerService(mockKafkaConsumer, TOPIC,
            new DefaultEndpoint(EndpointURL.parse("kafka-source")));
        service.setFlowName("test-flow");
        service.setProcessorName("test-processor");
        service.setExchangeFactory(TEST_EXCHANGE_FACTORY);
    }

    private static ConsumerRecords<Object, Object> batchOf(int count) {
        final List<ConsumerRecord<Object, Object>> records = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            records.add(new ConsumerRecord<>(TOPIC, 0, i, "key-" + i, "value-" + i));
        }
        final Map<TopicPartition, List<ConsumerRecord<Object, Object>>> byPartition =
            Collections.singletonMap(new TopicPartition(TOPIC, 0), records);
        return new ConsumerRecords<>(byPartition);
    }

    private static FlowNode countingFlowNode(AtomicInteger processedCount) {
        return new FlowNode() {
            @Override public void process(ExchangeImpl exchange) { processedCount.incrementAndGet(); }
            @Override public void setFlowName(String flowName) { }
            @Override public void setSourceEndpointResource(String sourceEndpointResource) { }
            @Override public void setProcessorName(String processorName) { }
            @Override public void setNext(FlowNode next) { }
            @Override public boolean hasNext() { return false; }
            @Override public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) { }
            @Override public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) { }
        };
    }

    private static FlowNode failingAtFlowNode(int failAtZeroBasedIndex) {
        final AtomicInteger index = new AtomicInteger(0);
        return new FlowNode() {
            @Override
            public void process(ExchangeImpl exchange) {
                if (index.getAndIncrement() == failAtZeroBasedIndex) {
                    throw new RuntimeException("simulated processing failure, no retry/error channel configured");
                }
            }
            @Override public void setFlowName(String flowName) { }
            @Override public void setSourceEndpointResource(String sourceEndpointResource) { }
            @Override public void setProcessorName(String processorName) { }
            @Override public void setNext(FlowNode next) { }
            @Override public boolean hasNext() { return false; }
            @Override public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) { }
            @Override public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) { }
        };
    }

    @Test
    public void shouldDurablyEnqueueAndCommitOnceForTheWholeBatchWithoutRunningThePipeline() {
        final AtomicInteger processedCount = new AtomicInteger(0);
        service.setNext(countingFlowNode(processedCount));

        final KafkaConsumerService.KafkaConsumerTask task = service.new KafkaConsumerTask(Duration.ofMillis(100));
        task.processBatch(batchOf(5));

        // processBatch(...) only writes through + enqueues (issue #71) - actual pipeline
        // execution is drained from the queue later by this consumer's own DispatchStrategy
        // (started by doStart(), never called in this unit test), so it must not have run yet.
        Assert.assertEquals("pipeline execution must be decoupled from the offset commit", 0, processedCount.get());
        verify(mockKafkaConsumer, times(1)).commitSync();
    }

    @Test
    public void shouldCommitEvenWhenTheDownstreamPipelineWouldEventuallyFail() {
        // Issue #71's actual behavior change: a downstream pipeline failure no longer blocks (or
        // undoes) the commit - the record is already durably captured in the inbox by the time
        // processBatch(...) returns, and a later pipeline failure is handled like any other
        // durable-inbox entry (retry/dead-letter on restart), not by withholding this commit.
        // failingAtFlowNode is never actually invoked here (see the test above) - it stands in
        // for "this node would throw if it ever ran", proving that alone cannot affect the commit.
        service.setNext(failingAtFlowNode(2));

        final KafkaConsumerService.KafkaConsumerTask task = service.new KafkaConsumerTask(Duration.ofMillis(100));
        task.processBatch(batchOf(5));

        verify(mockKafkaConsumer, times(1)).commitSync();
    }

    @Test
    public void shouldSkipCommitWhenARecordCannotBeDurablyWritten() throws IOException {
        // A plain file where the inbox needs a directory: Files.createDirectories(...) fails,
        // surfacing as an IllegalStateException out of enqueue()/process() - stands in for a
        // real local disk I/O failure, the one failure mode that must still withhold the commit
        // (see the companion design doc's §4 durability tradeoff).
        final Path blockedDirectory = temporaryFolder.newFile("blocked-inbox-directory").toPath();
        service.setDurableInbox(new SegmentedLogDurableInbox(
            blockedDirectory, "test-resource", new DistributedIdentityGeneratorImpl()));

        final KafkaConsumerService.KafkaConsumerTask task = service.new KafkaConsumerTask(Duration.ofMillis(100));
        task.processBatch(batchOf(3));

        verify(mockKafkaConsumer, never()).commitSync();
    }

    @Test
    public void shouldCommitTriviallyWhenProcessBatchIsCalledDirectlyWithZeroRecords() {
        // processBatch(...) itself has no empty-batch special case - it's run() that avoids ever
        // calling it on an empty poll (via records.isEmpty()), to skip a needless commitSync()
        // round-trip on every idle poll cycle. Documented here as a characterization test so a
        // future change to processBatch(...) alone doesn't silently start committing on empty
        // batches without run()'s guard - a caller other than run() would get a real, if useless,
        // commit rather than a skipped one.
        final AtomicInteger processedCount = new AtomicInteger(0);
        service.setNext(countingFlowNode(processedCount));

        final KafkaConsumerService.KafkaConsumerTask task = service.new KafkaConsumerTask(Duration.ofMillis(100));
        task.processBatch(batchOf(0));

        Assert.assertEquals(0, processedCount.get());
        verify(mockKafkaConsumer, times(1)).commitSync(); // batchFullyHandled trivially true for zero records
    }
}
