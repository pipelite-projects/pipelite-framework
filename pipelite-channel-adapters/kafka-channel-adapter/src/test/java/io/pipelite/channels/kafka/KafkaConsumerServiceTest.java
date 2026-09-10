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
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.FlowNode;
import io.pipelite.spi.flow.exchange.Message;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import io.pipelite.spi.flow.process.ExchangePostProcessor;
import io.pipelite.spi.flow.process.ExchangePreProcessor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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
 * Covers the #64 fix directly: {@code processBatch(...)} must commit a poll batch's offsets only
 * once every record in it has actually finished the pipeline, and must skip the commit entirely
 * if any record fails with nowhere configured to route the failure. Exercised without a real or
 * mocked {@code poll()} cycle via the package-private test-seam constructor and {@code
 * processBatch(...)}'s package visibility.
 */
public class KafkaConsumerServiceTest {

    private static final ExchangeFactory TEST_EXCHANGE_FACTORY = new ExchangeFactory() {
        @Override public Exchange createExchange() { return createExchange(null, null); }
        @Override public Exchange createExchange(Headers headers) { return createExchange(headers, null); }
        @Override public Exchange createExchange(Headers headers, Object inputPayload) {
            final Message message = new SimpleMessage(UUID.randomUUID().toString());
            message.setPayload(inputPayload);
            return new Exchange(message, headers);
        }
        @Override public Exchange createExchange(Object inputPayload) { return createExchange(null, inputPayload); }
        @Override public Exchange copyExchange(Exchange exchange) {
            return createExchange(exchange.getHeaders(), exchange.getInputPayloadAs(Object.class));
        }
        @Override public Exchange nextExchange(Exchange current) { return copyExchange(current); }
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
            @Override public void process(Exchange exchange) { processedCount.incrementAndGet(); }
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
            public void process(Exchange exchange) {
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
    public void shouldCommitOnceEveryRecordInTheBatchSucceeds() {
        final AtomicInteger processedCount = new AtomicInteger(0);
        service.setNext(countingFlowNode(processedCount));

        final KafkaConsumerService.KafkaConsumerTask task = service.new KafkaConsumerTask(Duration.ofMillis(100));
        task.processBatch(batchOf(5));

        Assert.assertEquals("every record in the batch must have reached the pipeline", 5, processedCount.get());
        verify(mockKafkaConsumer, times(1)).commitSync();
    }

    @Test
    public void shouldNotCommitWhenARecordFailsWithNoRetryOrErrorChannel() {
        service.setNext(failingAtFlowNode(2)); // fails on the 3rd record (index 2) of 5

        final KafkaConsumerService.KafkaConsumerTask task = service.new KafkaConsumerTask(Duration.ofMillis(100));
        task.processBatch(batchOf(5));

        verify(mockKafkaConsumer, never()).commitSync();
    }

    @Test
    public void shouldStillAttemptRemainingRecordsInTheBatchAfterOneFails() {
        // Best-effort within the run, even though the whole batch's commit is skipped either way
        // (see shouldNotCommitWhenARecordFailsWithNoRetryOrErrorChannel) - a batch-wide abort on
        // the first failure would silently skip records 4 and 5 without even attempting them.
        final AtomicInteger attempts = new AtomicInteger(0);
        service.setNext(new FlowNode() {
            @Override
            public void process(Exchange exchange) {
                final int current = attempts.incrementAndGet();
                if (current == 3) {
                    throw new RuntimeException("simulated failure on record 3");
                }
            }
            @Override public void setFlowName(String flowName) { }
            @Override public void setSourceEndpointResource(String sourceEndpointResource) { }
            @Override public void setProcessorName(String processorName) { }
            @Override public void setNext(FlowNode next) { }
            @Override public boolean hasNext() { return false; }
            @Override public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) { }
            @Override public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) { }
        });

        final KafkaConsumerService.KafkaConsumerTask task = service.new KafkaConsumerTask(Duration.ofMillis(100));
        task.processBatch(batchOf(5));

        Assert.assertEquals("all 5 records must have been attempted despite record 3 failing", 5, attempts.get());
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
