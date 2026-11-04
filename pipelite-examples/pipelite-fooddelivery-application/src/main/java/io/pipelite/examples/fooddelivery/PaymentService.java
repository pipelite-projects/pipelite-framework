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
 * Stands in for a payment gateway: authorizes the card of an order, which takes a moment (a call to an
 * external system) and is occasionally declined. It runs in {@code paymentAuthorizationFlow}, in parallel
 * with the kitchen, and {@code dispatchProcessingFlow} does not assign a driver until both have finished
 * with the order: that wait is the {@code barrier(...)} of {@link FoodDeliveryFlowConfiguration}.
 *
 * <p>The gateway is slower than a kitchen station on purpose, so that the payment is often the last one
 * to finish and the order really waits at the barrier. A declined card is not transient: the flow ends
 * with the failure, the barrier is told, and the order waiting at the dispatch stage does not wait for
 * anything else: it goes to the error handling of the dispatch flow at once.
 */
@Component
public class PaymentService {

    private static final double DECLINE_PROBABILITY = 0.05;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    public void authorize(Order order) {
        final int gatewayMillis = ThreadLocalRandom.current().nextInt(100, 600);
        try {
            Thread.sleep(gatewayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (ThreadLocalRandom.current().nextDouble() < DECLINE_PROBABILITY) {
            logger.warn("[payment] card declined for {} ({}ms)", order, gatewayMillis);
            throw new IllegalStateException("Payment declined for order " + order.getOrderId());
        }

        logger.info("[payment] authorized {} ({}ms) on thread {}", order, gatewayMillis, Thread.currentThread().getName());
    }

}
