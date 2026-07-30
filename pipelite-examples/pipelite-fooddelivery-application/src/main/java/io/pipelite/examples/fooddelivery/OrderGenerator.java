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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Load generator: alternates between POSTing orders to the HTTP ingress and appending lines to
 * the tailed partner-orders file, so the demo shows genuine concurrent overlap across the
 * kitchen/dispatch stages without any manual curl calls or file edits. Unlike the original
 * self-driven background thread, this is now triggered externally, once per invocation, by
 * {@code orderGeneratorFlow}'s {@code time://} source in {@link FoodDeliveryFlowConfiguration} —
 * {@code time://} fires on a fixed schedule with no jitter and no built-in "stop after N" (see
 * that flow's javadoc), so the pacing/jitter this class used to do internally is gone, and the
 * "stop after N" cap is instead enforced here by turning every tick past
 * {@value #TOTAL_ORDERS_TO_GENERATE} into a cheap no-op.
 */
@Component
class OrderGenerator {

    private static final int TOTAL_ORDERS_TO_GENERATE = 40;

    private static final Logger logger = LoggerFactory.getLogger(OrderGenerator.class);

    private static final String HTTP_ORDERS_ENDPOINT = "http://localhost:80/orders";

    private static final List<String> RESTAURANTS =
        List.of("Pizzeria Da Mario", "Sushi Time", "Burger House", "Taco Fiesta", "Green Bowl");
    private static final List<String> CUSTOMERS =
        List.of("Alice Rossi", "Bob Bianchi", "Chiara Verdi", "Davide Neri", "Elena Conti");
    // deliberately free of ',' and '|' so both the HTTP (pipe-delimited) and partner-file
    // (comma-delimited) wire formats can split on exactly 5 fields without ambiguity
    private static final List<String> ITEM_SETS = List.of(
        "2x Margherita + 1x Coke", "8pz Sashimi Mix", "1x Cheeseburger Menu", "3x Tacos al Pastor", "1x Quinoa Bowl");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private int orderSequence = 0;
    private int emittedCount = 0;
    private boolean finished = false;
    private boolean filesPrepared = false;

    // Single-threaded by construction: time:// sources run on one dedicated poller thread per
    // flow (see EventDrivenConsumerService's "one thread per flow" model discussed throughout
    // this project's concurrency work), so no synchronization is needed on the counters above.
    void generateAndEmitNextOrder() {

        if (finished) {
            return;
        }
        if (emittedCount >= TOTAL_ORDERS_TO_GENERATE) {
            finished = true;
            logger.info("[generator] finished emitting {} simulated orders", TOTAL_ORDERS_TO_GENERATE);
            return;
        }
        if (!filesPrepared) {
            prepareFiles();
            filesPrepared = true;
        }

        final int index = emittedCount++;
        final String orderId = String.format("ORD-%04d", ++orderSequence);
        final String restaurant = pick(RESTAURANTS);
        final String customer = pick(CUSTOMERS);
        final String items = pick(ITEM_SETS);
        // Locale.ROOT: the default locale may use ',' as the decimal separator (e.g. it_IT),
        // which would break BigDecimal parsing on the consuming side (Order.parse)
        final String amount = String.format(java.util.Locale.ROOT, "%.2f", ThreadLocalRandom.current().nextDouble(8.0, 45.0));

        // roughly 1-in-3 orders arrive via the partner CSV channel, the rest via HTTP
        if (index % 3 == 0) {
            appendPartnerOrder(orderId, restaurant, customer, items, amount);
        } else {
            postHttpOrder(orderId, restaurant, customer, items, amount);
        }
    }

    private void prepareFiles() {
        try {
            Files.createDirectories(FoodDeliveryPaths.PARTNER_ORDERS_FILE.getParent());
            if (!Files.exists(FoodDeliveryPaths.PARTNER_ORDERS_FILE)) {
                Files.createFile(FoodDeliveryPaths.PARTNER_ORDERS_FILE);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to prepare partner-orders file", e);
        }
    }

    private void postHttpOrder(String orderId, String restaurant, String customer, String items, String amount) {
        final String body = String.join("|", orderId, restaurant, customer, items, amount);
        final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(HTTP_ORDERS_ENDPOINT))
            .timeout(Duration.ofSeconds(5))
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        try {
            final HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            logger.info("[generator] posted {} via HTTP (status={})", orderId, response.statusCode());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("[generator] failed to POST {} to {}: {}", orderId, HTTP_ORDERS_ENDPOINT, e.getMessage());
        }
    }

    private void appendPartnerOrder(String orderId, String restaurant, String customer, String items, String amount) {
        final String line = String.join(",", orderId, restaurant, customer, items, amount) + System.lineSeparator();
        try {
            Files.writeString(FoodDeliveryPaths.PARTNER_ORDERS_FILE, line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            logger.info("[generator] appended {} to partner-orders file", orderId);
        } catch (IOException e) {
            logger.warn("[generator] failed to append {} to partner-orders file: {}", orderId, e.getMessage());
        }
    }

    private static String pick(List<String> options) {
        return options.get(ThreadLocalRandom.current().nextInt(options.size()));
    }

}
