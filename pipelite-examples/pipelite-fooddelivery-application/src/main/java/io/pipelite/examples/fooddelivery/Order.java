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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

/**
 * Plain mutable JavaBean (default Jackson binding, no annotations needed) flowing through every
 * stage of the demo: parsed from the two ingress channels ({@link #fromHttpPayload(String)},
 * {@link #fromCsvLine(String)}), enriched in-place by {@link KitchenService} and
 * {@link DispatchService}, and serialized for the Kafka hop / final audit log.
 */
public class Order {

    private String orderId;
    private String restaurantName;
    private String customerName;
    private String itemsSummary;
    private BigDecimal totalAmount;
    private String sourceChannel;
    private OrderStatus status;
    private String driverId;
    private Integer etaMinutes;

    public Order() {
    }

    private Order(String orderId, String restaurantName, String customerName, String itemsSummary,
                   BigDecimal totalAmount, String sourceChannel) {
        this.orderId = orderId;
        this.restaurantName = restaurantName;
        this.customerName = customerName;
        this.itemsSummary = itemsSummary;
        this.totalAmount = totalAmount;
        this.sourceChannel = sourceChannel;
        this.status = OrderStatus.RECEIVED;
    }

    /**
     * Wire format for the HTTP ingress: pipe-delimited
     * {@code orderId|restaurantName|customerName|itemsSummary|totalAmount}.
     */
    public static Order fromHttpPayload(String rawBody) {
        return parse(rawBody, "\\|", "http");
    }

    /**
     * Wire format for the partner-file ingress: same fields, comma-delimited.
     */
    public static Order fromCsvLine(String line) {
        return parse(line, ",", "file-partner");
    }

    private static Order parse(String raw, String delimiterRegex, String sourceChannel) {
        final String[] fields = raw.trim().split(delimiterRegex, 5);
        if (fields.length != 5) {
            throw new IllegalArgumentException(
                "Expected 5 fields (orderId, restaurant, customer, items, amount), got: " + raw);
        }
        return new Order(fields[0].trim(), fields[1].trim(), fields[2].trim(), fields[3].trim(),
            new BigDecimal(fields[4].trim()), sourceChannel);
    }

    public String toKafkaEventJson(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize order " + orderId + " to JSON", e);
        }
    }

    public static Order fromKafkaEventJson(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, Order.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize order event: " + json, e);
        }
    }

    /**
     * One audit-log line, including its own line terminator: the file adapter's
     * {@code LineRecordMapper} strips terminators on read, so writers must supply their own.
     */
    public String toDeliveryLogLine() {
        return String.format("%s | %s -> %s | %s | total=%s | driver=%s | eta=%dmin | status=%s\n",
            orderId, restaurantName, customerName, itemsSummary, totalAmount, driverId, etaMinutes, status);
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getRestaurantName() {
        return restaurantName;
    }

    public void setRestaurantName(String restaurantName) {
        this.restaurantName = restaurantName;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getItemsSummary() {
        return itemsSummary;
    }

    public void setItemsSummary(String itemsSummary) {
        this.itemsSummary = itemsSummary;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(String sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public Integer getEtaMinutes() {
        return etaMinutes;
    }

    public void setEtaMinutes(Integer etaMinutes) {
        this.etaMinutes = etaMinutes;
    }

    @Override
    public String toString() {
        return String.format("Order{%s, %s -> %s, %s, total=%s, channel=%s, status=%s}",
            orderId, restaurantName, customerName, itemsSummary, totalAmount, sourceChannel, status);
    }
}
