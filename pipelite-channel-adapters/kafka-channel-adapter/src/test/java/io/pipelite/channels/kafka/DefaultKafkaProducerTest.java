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

import io.pipelite.spi.endpoint.Endpoint;

import io.pipelite.channels.kafka.config.KafkaChannelConfiguration;
import io.pipelite.channels.kafka.support.KafkaConstants;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * What the Kafka producer does with an exchange, and, since issue #159, that it releases the Kafka
 * producer it holds when it is closed. Before, the producer of a flow was never closed (its
 * context-stopped hook was never called), and neither was the one created for every exchange
 * supplied to a {@code kafka://} destination: a connection and a sender thread leaked each time.
 */
public class DefaultKafkaProducerTest {

    private KafkaProducer<Object, Object> kafkaProducer;
    private DefaultKafkaProducer subject;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        kafkaProducer = mock(KafkaProducer.class);
        subject = new DefaultKafkaProducer((Endpoint) () -> EndpointURL.parse("orders"),
            mock(KafkaChannelConfiguration.class), kafkaProducer);
    }

    private static ExchangeImpl exchangeWith(Object payload, Object key) {
        final ExchangeImpl exchange = new ExchangeImpl(new SimpleMessage("test-id"));
        exchange.setInputPayload(payload);
        if (key != null) {
            exchange.putHeader(KafkaConstants.KAFKA_RECORD_KEY_EXCHANGE_HEADER_NAME, key);
        }
        return exchange;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void givenAnExchange_thenTheRecordIsSentToTheTopicWithTheKeyHeaderAndTheValuePayloadAndFlushed() {
        subject.produce(exchangeWith("a value", "a key"));

        final ArgumentCaptor<ProducerRecord<Object, Object>> record = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaProducer).send(record.capture());
        Assert.assertEquals("orders", record.getValue().topic());
        Assert.assertEquals("a key", record.getValue().key());
        Assert.assertEquals("a value", record.getValue().value());
        verify(kafkaProducer).flush();
    }

    @Test
    public void givenAnExchangeWithoutPayload_thenNothingIsSent() {
        subject.produce(exchangeWith(null, "a key"));

        verify(kafkaProducer, never()).send(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void givenTheProducerIsClosed_thenTheKafkaProducerIsClosedOnce() {
        subject.close();

        verify(kafkaProducer, times(1)).close();
    }
}
