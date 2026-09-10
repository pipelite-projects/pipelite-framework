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
package io.pipelite.spi.flow.concurrent;

import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class QueuePressureGateTest {

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNegativeLowWatermark() {
        new QueuePressureGate(10, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectHighWatermarkNotGreaterThanLow() {
        new QueuePressureGate(5, 5);
    }

    @Test
    public void shouldDeriveLowWatermarkAsQuarterOfHighByDefault() throws Exception {
        // withDefaultHysteresis(20) must derive lowWatermark=5 (20/4) - verified indirectly by
        // observing exactly when a blocked producer releases, since the fields themselves are
        // private.
        final QueuePressureGate gate = QueuePressureGate.withDefaultHysteresis(20);
        final AtomicInteger queueSize = new AtomicInteger(20);
        final AtomicBoolean unblocked = new AtomicBoolean(false);

        final Thread producer = new Thread(() -> {
            try {
                gate.beforeEnqueue(queueSize::get); // engages pressure (size >= 20)
                unblocked.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(gate::isPressured);

        queueSize.set(6);
        gate.afterDequeue(queueSize::get);
        Thread.sleep(200);
        Assert.assertFalse("still above the derived low watermark (5): must stay pressured", unblocked.get());

        queueSize.set(5);
        gate.afterDequeue(queueSize::get);
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(unblocked::get);

        producer.join(2000);
        Assert.assertFalse(producer.isAlive());
    }

    @Test
    public void shouldNotBlockWhileUnderHighWatermark() throws InterruptedException {
        final QueuePressureGate gate = new QueuePressureGate(10, 2);
        // Must return immediately - if this were to block, the test would hang and time out.
        gate.beforeEnqueue(() -> 9);
        Assert.assertFalse(gate.isPressured());
    }

    @Test
    public void shouldBlockOnceHighWatermarkReachedAndReleaseOnlyAtLowWatermark() throws Exception {
        final QueuePressureGate gate = new QueuePressureGate(10, 3);
        final AtomicInteger queueSize = new AtomicInteger(10);
        final AtomicBoolean unblocked = new AtomicBoolean(false);

        final Thread producer = new Thread(() -> {
            try {
                gate.beforeEnqueue(queueSize::get);
                unblocked.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        producer.start();

        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(gate::isPressured);
        Assert.assertFalse("must still be blocked: queue hasn't drained at all yet", unblocked.get());

        // Drain down to just above the low watermark (3): hysteresis means this must NOT release
        // yet, even though it's well below the high watermark (10) that engaged pressure.
        queueSize.set(4);
        gate.afterDequeue(queueSize::get);
        Thread.sleep(200); // give the producer thread a chance to wake up wrongly, if it would
        Assert.assertFalse("must stay blocked above the low watermark despite being under high watermark", unblocked.get());
        Assert.assertTrue(gate.isPressured());

        // Now actually reach the low watermark: this must release it.
        queueSize.set(3);
        gate.afterDequeue(queueSize::get);

        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(unblocked::get);
        Assert.assertFalse(gate.isPressured());

        producer.join(2000);
        Assert.assertFalse(producer.isAlive());
    }

    @Test
    public void shouldWakeAllBlockedProducersOnRelease() throws Exception {
        final QueuePressureGate gate = new QueuePressureGate(5, 1);
        final AtomicInteger queueSize = new AtomicInteger(5);
        final int producerCount = 4;
        final AtomicInteger unblockedCount = new AtomicInteger(0);
        final Thread[] producers = new Thread[producerCount];

        for (int i = 0; i < producerCount; i++) {
            producers[i] = new Thread(() -> {
                try {
                    gate.beforeEnqueue(queueSize::get);
                    unblockedCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            producers[i].start();
        }

        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(gate::isPressured);
        Assert.assertEquals(0, unblockedCount.get());

        queueSize.set(1);
        gate.afterDequeue(queueSize::get);

        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(() -> unblockedCount.get() == producerCount);
        for (Thread producer : producers) {
            producer.join(2000);
            Assert.assertFalse(producer.isAlive());
        }
    }
}
