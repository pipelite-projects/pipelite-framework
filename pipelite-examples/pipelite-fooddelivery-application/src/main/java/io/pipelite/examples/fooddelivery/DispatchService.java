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
package io.pipelite.examples.fooddelivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for a real dispatch/fleet-management system: assigns a driver and an ETA. Invoked
 * concurrently by up to {@code concurrency=3} threads of the shared source worker pool (see
 * {@code dispatchProcessingFlow} in {@link FoodDeliveryFlowConfiguration}) — a second, independent
 * concurrency budget from the kitchen stage, both drawn from the same framework-owned shared pool.
 * {@code @Component} so Spring constructor-injects it into {@link FoodDeliveryFlowConfiguration}.
 */
@Component
public class DispatchService {

    // Package-visible: FoodDeliveryFlowConfiguration iterates this same list to build one
    // routing-table branch (and one output file) per known driver.
    static final List<String> DRIVER_POOL = List.of("Marco", "Giulia", "Luca", "Sara", "Davide");

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final AtomicInteger nextDriverIndex = new AtomicInteger(0);

    public Order assignDriver(Order order) {
        final String driverId = DRIVER_POOL.get(nextDriverIndex.getAndIncrement() % DRIVER_POOL.size());
        final int etaMinutes = ThreadLocalRandom.current().nextInt(10, 40);

        logger.info("[dispatch] assigning driver {} to {} on thread {}", driverId, order, Thread.currentThread().getName());

        final int coordinationMillis = ThreadLocalRandom.current().nextInt(30, 150);
        try {
            Thread.sleep(coordinationMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        order.setDriverId(driverId);
        order.setEtaMinutes(etaMinutes);
        order.setStatus(OrderStatus.DISPATCHED);
        logger.info("[dispatch] dispatched {} on thread {}", order, Thread.currentThread().getName());
        return order;
    }

}
