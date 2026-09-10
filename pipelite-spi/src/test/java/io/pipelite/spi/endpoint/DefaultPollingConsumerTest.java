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
package io.pipelite.spi.endpoint;

import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.Message;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Focused on the {@link QueuePressureGate} wiring inside {@code consume()}/{@code receive()} —
 * default high/low watermarks are 20/5 (see {@code DefaultPollingConsumer}'s own constant).
 */
public class DefaultPollingConsumerTest {

    private static Exchange exchange(int id) {
        final Message message = new SimpleMessage("Id#" + id);
        message.setPayload("Payload#" + id);
        return new Exchange(message);
    }

    private static DefaultPollingConsumer newConsumer() {
        return new DefaultPollingConsumer(new DefaultEndpoint(EndpointURL.parse("start-endpoint")));
    }

    @Test
    public void shouldNotBlockConsumeWhileUnderHighWatermark() {
        final DefaultPollingConsumer consumer = newConsumer();
        for (int i = 1; i <= 19; i++) {
            consumer.consume(exchange(i)); // must not block: all under the high watermark (20)
        }
    }

    @Test
    public void shouldBlockConsumeOnceHighWatermarkReachedAndReleaseAtLowWatermark() throws Exception {
        final DefaultPollingConsumer consumer = newConsumer();
        for (int i = 1; i <= 20; i++) {
            consumer.consume(exchange(i)); // fills exactly to the high watermark (20)
        }

        final AtomicBoolean accepted = new AtomicBoolean(false);
        final Thread producer = new Thread(() -> {
            consumer.consume(exchange(21)); // queue is already at the high watermark: must block
            accepted.set(true);
        });
        producer.start();

        Thread.sleep(300);
        Assert.assertFalse("must block: queue is already at the high watermark", accepted.get());

        // Drain down to 6 (still above the low watermark of 5): must NOT release yet.
        for (int i = 0; i < 14; i++) {
            consumer.receive();
        }
        Thread.sleep(300);
        Assert.assertFalse("must stay blocked above the low watermark despite being under the high watermark", accepted.get());

        // Drain one more, reaching the low watermark (5): must release.
        consumer.receive();
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(accepted::get);

        producer.join(2000);
        Assert.assertFalse(producer.isAlive());
    }

    @Test
    public void shouldReturnNullFromReceiveOnEmptyQueueWithoutTouchingTheGate() {
        final DefaultPollingConsumer consumer = newConsumer();
        Assert.assertNull(consumer.receive());
        Assert.assertNull(consumer.receiveNoWait());
    }
}
