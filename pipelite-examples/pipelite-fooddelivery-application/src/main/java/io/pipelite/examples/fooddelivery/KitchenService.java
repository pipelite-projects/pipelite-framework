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

import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for a real kitchen-management system: simulates a station taking time to prepare an
 * order. Invoked concurrently by up to {@code concurrency=4} dispatch threads of the shared
 * source worker pool (see {@code kitchenProcessingFlow} in {@link FoodDeliveryFlowConfiguration}),
 * so the random sleep here is what makes the overlap between "kitchen stations" visible in the
 * console log. {@code @Component} so Spring constructor-injects it into
 * {@link FoodDeliveryFlowConfiguration}.
 *
 * <p>Also throws an occasional transient "equipment glitch" (see
 * {@link #EQUIPMENT_GLITCH_PROBABILITY}) — a failure with no lasting cause, so it's expected to
 * succeed on a later attempt. This is what {@code kitchenProcessingFlow}'s
 * {@code .withRetryChannel(...)} exists to demonstrate: at ~20% failure probability per attempt,
 * the odds of exhausting all 3 configured attempts in a row are under 1%, so in practice this
 * shows retries recovering, not messages being dropped.
 */
@Component
public class KitchenService {

    private static final double EQUIPMENT_GLITCH_PROBABILITY = 0.2;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public Order prepareOrder(Order order) {
        order.setStatus(OrderStatus.PREPARING);
        logger.info("[kitchen] preparing {} on thread {}", order, Thread.currentThread().getName());

        if (ThreadLocalRandom.current().nextDouble() < EQUIPMENT_GLITCH_PROBABILITY) {
            logger.warn("[kitchen] equipment glitch while preparing {} - retry channel should redeliver", order);
            throw new IllegalStateException("Kitchen equipment glitch while preparing " + order.getOrderId());
        }

        final int prepMillis = ThreadLocalRandom.current().nextInt(50, 200);
        try {
            Thread.sleep(prepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        order.setStatus(OrderStatus.READY_FOR_DISPATCH);
        logger.info("[kitchen] ready {} ({}ms prep) on thread {}", order, prepMillis, Thread.currentThread().getName());
        return order;
    }

}
