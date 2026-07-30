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

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes one delivery record to its courier's output file, serialized per destination file.
 *
 * <p>Originally this write went through {@code toRoute(...)} straight to a {@code file://}
 * sink, one per driver. With {@code dispatchProcessingFlow}'s {@code concurrency=3}, two
 * concurrent dispatch workers can land on the same driver's file at the same time — each
 * {@code FileProducer.process()} call opens/writes/closes independently with no coordination
 * across calls, and on Windows this occasionally threw {@code FileSystemException: the file is
 * used by another process}, silently dropping that delivery record (confirmed empirically: 2 of
 * 39 dispatched orders lost in one run). A {@code synchronized} block keyed per destination
 * file — the same fix a real production sink would need, since the underlying race exists
 * independent of Pipelite's own routing/sink mechanics — closes that gap while keeping the
 * one-file-per-courier split.
 */
@Component
class DeliveryLogWriter {

    private final Map<Path, Object> fileLocksByPath = new ConcurrentHashMap<>();

    void writeRecord(String driverId, String line) {

        final Path file = DispatchService.DRIVER_POOL.contains(driverId)
            ? FoodDeliveryPaths.deliveryLogFileForDriver(driverId)
            : FoodDeliveryPaths.DELIVERY_LOG_FALLBACK_FILE;

        final Object lock = fileLocksByPath.computeIfAbsent(file, path -> new Object());
        synchronized (lock) {
            try {
                Files.createDirectories(file.getParent());
                Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to write delivery record to " + file, e);
            }
        }
    }

}
