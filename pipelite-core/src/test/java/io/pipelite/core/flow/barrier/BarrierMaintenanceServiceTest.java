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
package io.pipelite.core.flow.barrier;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Issue #150: the barriers are looked after on a schedule that survives a failing run and stops with the context. */
public class BarrierMaintenanceServiceTest {

    @Test
    public void givenARunThatFails_thenTheMaintenanceRunsAgain() throws Exception {
        final BarrierCoordinator coordinator = Mockito.mock(BarrierCoordinator.class);
        final AtomicInteger runs = new AtomicInteger();
        final CountDownLatch threeRuns = new CountDownLatch(3);
        Mockito.doAnswer(invocation -> {
            threeRuns.countDown();
            if (runs.incrementAndGet() == 1) {
                throw new IllegalStateException("the first run fails");
            }
            return null;
        }).when(coordinator).maintain();
        final BarrierMaintenanceService service = new BarrierMaintenanceService(coordinator, Duration.ofMillis(20));

        service.start();
        try {
            Assert.assertTrue("it keeps running after a failure", threeRuns.await(10, TimeUnit.SECONDS));
        } finally {
            service.stop();
        }
    }

    @Test
    public void givenTheServiceStopped_thenNoMoreRuns() throws Exception {
        final BarrierCoordinator coordinator = Mockito.mock(BarrierCoordinator.class);
        final BarrierMaintenanceService service = new BarrierMaintenanceService(coordinator, Duration.ofMillis(20));
        service.start();
        Mockito.verify(coordinator, Mockito.timeout(10_000).atLeastOnce()).maintain();

        service.stop();
        final int runs = Mockito.mockingDetails(coordinator).getInvocations().size();
        Thread.sleep(150);

        Assert.assertEquals(runs, Mockito.mockingDetails(coordinator).getInvocations().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenAPeriodThatIsNotPositive_thenItIsRefused() {
        new BarrierMaintenanceService(Mockito.mock(BarrierCoordinator.class), Duration.ZERO);
    }
}
