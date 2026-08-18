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

import java.nio.file.Path;
import java.util.Locale;

/**
 * Shared demo file locations, referenced both by {@link FoodDeliveryFlowConfiguration} (which
 * flow tails/writes them) and {@link OrderGenerator} / {@link Application} (which seed the
 * partner file and reset state between runs). Kept as plain static constants rather than
 * injected via the {@code DependencyRegistry}, since that registry resolves {@code @DefineFlow}
 * parameters by type — two distinct {@code String}/{@code Path} values of the same type would
 * be ambiguous to resolve.
 */
final class FoodDeliveryPaths {

    private static final Path DEMO_ROOT = Path.of(System.getProperty("java.io.tmpdir"), "pipelite-fooddelivery-demo");

    /**
     * Fallback destination for a dispatched order whose {@code driverId} doesn't match any of
     * the known per-driver buckets — the {@code otherwise(...)} branch of the dispatch stage's
     * routing table (see {@link FoodDeliveryFlowConfiguration}) always requires a default.
     */
    static final Path DELIVERY_LOG_FALLBACK_FILE = DEMO_ROOT.resolve("delivery-log-unassigned.csv");
    static final Path PARTNER_ORDERS_FILE = DEMO_ROOT.resolve("partner-orders.csv");
    static final Path FILE_ADAPTER_STATE_DIRECTORY = DEMO_ROOT.resolve("state");

    /**
     * Where {@code dispatchDeadLetterFlow} (see {@link FoodDeliveryFlowConfiguration}) writes
     * orders that exceeded the manual-review threshold and were routed to the dead letter
     * channel after exhausting {@code dispatchProcessingFlow}'s retry attempts.
     */
    static final Path REJECTED_ORDERS_FILE = DEMO_ROOT.resolve("rejected-orders.log");

    private FoodDeliveryPaths() {
    }

    /**
     * One distinct output file per courier, so deliveries can be inspected/audited per driver
     * rather than in one combined log.
     */
    static Path deliveryLogFileForDriver(String driverId) {
        return DEMO_ROOT.resolve(String.format("delivery-log-%s.csv", driverId.toLowerCase(Locale.ROOT)));
    }

}
