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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pipelite.core.Pipelite;
import io.pipelite.dsl.annotation.DefineFlow;
import io.pipelite.dsl.annotation.FlowConfiguration;
import io.pipelite.dsl.definition.FlowDefinition;

import java.math.BigDecimal;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seven flows: a {@code time://}-driven load generator, two ingress channels (HTTP + tailed file)
 * feeding one link-chained, concurrent kitchen stage, a Kafka hop into a second concurrent
 * dispatch stage, which finally routes each delivery to one of several per-courier output files
 * — plus a dead letter flow for orders the dispatch stage refuses to auto-approve:
 *
 * <pre>
 * time://order-generator-tick ─► [random ~40% drop] ─► OrderGenerator.generateAndEmitNextOrder()
 *                                                            │ (emits via HTTP or partner file)
 *                                                            ▼
 * http://orders               ─┐
 * file://.../partner-orders   ─┼─► link://kitchen-start   (concurrency=4, "kitchen stations")
 *                               │       │  .withRetryChannel(maxAttempts=3) — transient
 *                               │       │  "equipment glitch" (see KitchenService), retried in
 *                               │       │  place, never dead-lettered
 *                               │       ▼
 *                               │   kafka://order-events  (publish)
 *                               │       │
 *                               │       ▼
 *                               │   kafka://order-events?group.id=dispatch-service  (consume)
 *                               │       │
 *                               └─► link://dispatch-start (concurrency=3, "dispatch coordinators")
 *                                       │  .withRetryChannel(maxAttempts=2)
 *                                       │  .withErrorChannel(definedFlow("dispatch-dead-letter"))
 *                                       │  validate-order-amount: orders over the manual-review
 *                                       │  threshold always fail (deterministic, not transient) —
 *                                       │  retried twice regardless, then dead-lettered
 *                                       ▼
 *                                  DeliveryLogWriter: one file://.../delivery-log-&lt;driver&gt;.csv per courier
 *
 * definedFlow("dispatch-dead-letter") ─► dispatchDeadLetterFlow (resolved by its own defineFlow
 *                                         name, "dispatch-dead-letter" — not by fromSource)
 *                                             ─► file://.../rejected-orders.log
 * </pre>
 *
 * <p>{@code concurrency} is only accepted on the two bare, protocol-less {@code link://} target
 * sources ({@code kitchen-start}, {@code dispatch-start}) — {@code DefaultEndpointFactory}
 * rejects it fast on any {@code http://}/{@code file://}/{@code kafka://}/{@code time://} URL.
 * The Kafka hop exists specifically so the dispatch stage gets its own, separately-sized
 * concurrency budget: a single flow can't declare {@code concurrency} on both ends of a
 * channel-adapter boundary.
 *
 * <p>The per-courier split was originally a {@code toRoute(...)} dynamic routing table (one
 * {@code file://} destination per driver), but with {@code dispatchProcessingFlow}'s
 * {@code concurrency=3}, two workers landing on the same driver's file at the same time hit
 * Windows {@code FileSystemException}s — each {@code FileProducer.process()} call opens/writes/
 * closes independently, with no coordination across concurrent calls to the same path.
 * {@link DeliveryLogWriter} fixes this with a {@code synchronized} block keyed per destination
 * file, called directly from the {@code write-delivery-record} process step instead of through
 * a sink URL.
 *
 * <p><b>Retry channel / dead letter channel demo (issue #5)</b>: {@code kitchenProcessingFlow}
 * shows {@code .withRetryChannel(...)} used alone — a transient failure that's expected to
 * eventually succeed, so no dead letter channel is configured; exhausting {@code maxAttempts}
 * would just drop the message (the documented fallback), which in practice essentially never
 * happens here since the failure probability is independent per attempt.
 * {@code dispatchProcessingFlow} shows the two composed: a deterministic validation failure
 * (order total above {@link #MANUAL_REVIEW_THRESHOLD}) can never succeed no matter how many
 * times it's retried, so after {@code maxAttempts} the dead letter channel — not silent drop —
 * is what makes the order visible again, in {@code dispatchDeadLetterFlow}.
 *
 * <p>Registered via {@code @EnablePipelite(flowConfigurations = FoodDeliveryFlowConfiguration.class)}
 * on {@link Application}, so Spring constructs this class as a regular bean — constructor
 * injection applies.
 */
@FlowConfiguration
public class FoodDeliveryFlowConfiguration {

    private static final int ORDER_GENERATOR_TICK_PERIOD_MILLIS = 500;
    // Fraction of ticks randomly dropped by orderGeneratorFlow's filter, so the effective
    // inter-arrival time between generated orders is uneven rather than a flat 500ms.
    private static final double TICK_DROP_PROBABILITY = 0.4;
    private static final int KITCHEN_CONCURRENCY = 4;
    private static final int DISPATCH_CONCURRENCY = 3;
    private static final String ORDER_EVENTS_TOPIC = "order-events";
    private static final String DISPATCH_CONSUMER_GROUP = "dispatch-service";
    private static final String TICK_ROLL_HEADER = "tickRoll";
    private static final int KITCHEN_MAX_ATTEMPTS = 3;
    private static final int DISPATCH_MAX_ATTEMPTS = 2;
    private static final String DISPATCH_DEAD_LETTER_FLOW = "dispatch-dead-letter-flow";
    // Orders at or below this total are auto-dispatched; above it, validate-order-amount always
    // fails — deterministic, not transient, so retrying doesn't help and the dead letter channel
    // (not silent drop) is what makes the order visible again for manual handling.
    private static final BigDecimal MANUAL_REVIEW_THRESHOLD = BigDecimal.valueOf(200);

    private final KitchenService kitchenService;
    private final DispatchService dispatchService;
    private final ObjectMapper objectMapper;
    private final OrderGenerator orderGenerator;
    private final DeliveryLogWriter deliveryLogWriter;

    public FoodDeliveryFlowConfiguration(KitchenService kitchenService, DispatchService dispatchService,
                                          ObjectMapper objectMapper, OrderGenerator orderGenerator,
                                          DeliveryLogWriter deliveryLogWriter) {
        this.kitchenService = kitchenService;
        this.dispatchService = dispatchService;
        this.objectMapper = objectMapper;
        this.orderGenerator = orderGenerator;
        this.deliveryLogWriter = deliveryLogWriter;
    }

    /**
     * The {@code filter(...)} DSL step only evaluates a String expression (via a custom engine
     * whose only registered functions are {@code NOT}/{@code IF}/{@code SQRT}/{@code ROUND}/
     * {@code beanProperty} — no {@code random()}/{@code rand()} of any kind, confirmed against
     * {@code DefaultFunctionRegistryConfiguration}), so it cannot generate randomness by itself.
     * {@code roll-tick-dice} computes the random value in plain Java and stores it as a header
     * (as a {@code BigDecimal}, matching what {@code GteOperator} casts its operands to); the
     * following {@code filter(...)} then just reads that header back and compares it — the
     * actual randomness lives entirely in the preceding {@code process} step, not in the
     * expression itself.
     */
    @DefineFlow
    public FlowDefinition orderGeneratorFlow() {
        return Pipelite.defineFlow("order-generator-flow")
            .fromSource(String.format("time://order-generator-tick?period=%d&timeUnit=MILLISECONDS", ORDER_GENERATOR_TICK_PERIOD_MILLIS))
            .process("roll-tick-dice", (ioContext, contribution) ->
                ioContext.putHeader(TICK_ROLL_HEADER, BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble())))
            .filter("random-tick-jitter", String.format("Headers['%s'] >= %s", TICK_ROLL_HEADER, TICK_DROP_PROBABILITY))
            .process("generate-and-emit-order", (ioContext, contribution) -> orderGenerator.generateAndEmitNextOrder())
            .build();
    }

    @DefineFlow
    public FlowDefinition orderIngressFlow() {
        return Pipelite.defineFlow("order-ingress-flow")
            .fromSource("http://orders")
            .transformPayload("parse-http-order", payload -> Order.fromHttpPayload(payload.getPayloadAs(String.class)))
            .wireTap("log-incoming-http-order", "slf4j://orders-received")
            .toSink("link://kitchen-start")
            .build();
    }

    @DefineFlow
    public FlowDefinition partnerOrdersFileFlow() {
        final String fileSourceUrl = String.format("file://%s?startPosition=end&period=250", FoodDeliveryPaths.PARTNER_ORDERS_FILE);
        return Pipelite.defineFlow("partner-orders-file-flow")
            .fromSource(fileSourceUrl)
            .transformPayload("parse-partner-order", payload -> Order.fromCsvLine(payload.getPayloadAs(String.class)))
            .wireTap("log-incoming-partner-order", "slf4j://orders-received")
            .toSink("link://kitchen-start")
            .build();
    }

    @DefineFlow
    public FlowDefinition kitchenProcessingFlow() {
        return Pipelite.defineFlow("kitchen-processing-flow")
            .fromSource(String.format("kitchen-start?concurrency=%d", KITCHEN_CONCURRENCY))
            .process("prepare-order", (ioContext, contribution) ->
                ioContext.setOutputPayload(kitchenService.prepareOrder(ioContext.getInputPayloadAs(Order.class))))
            .transformPayload("serialize-order-event", payload -> payload.getPayloadAs(Order.class).toKafkaEventJson(objectMapper))
            .toSink(String.format("kafka://%s", ORDER_EVENTS_TOPIC))
            // Retry channel alone: KitchenService.prepareOrder's occasional "equipment glitch" is
            // transient, so redelivering the same order (resumed directly at prepare-order, not
            // replayed from the flow's source) is expected to eventually succeed. No dead letter
            // channel configured here - see the class Javadoc for why that's the deliberate choice.
            .withRetryChannel(retry -> retry.maxAttempts(KITCHEN_MAX_ATTEMPTS))
            .build();
    }

    @DefineFlow
    public FlowDefinition dispatchIngressFlow() {
        return Pipelite.defineFlow("dispatch-ingress-flow")
            .fromSource(String.format("kafka://%s?group.id=%s", ORDER_EVENTS_TOPIC, DISPATCH_CONSUMER_GROUP))
            .transformPayload("parse-order-event", payload -> Order.fromKafkaEventJson(payload.getPayloadAs(String.class), objectMapper))
            .toSink("link://dispatch-start")
            .build();
    }

    @DefineFlow
    public FlowDefinition dispatchProcessingFlow() {
        return Pipelite.defineFlow("dispatch-processing-flow")
            .fromSource(String.format("dispatch-start?concurrency=%d", DISPATCH_CONCURRENCY))
            .process("validate-order-amount", (ioContext, contribution) -> {
                final Order order = ioContext.getInputPayloadAs(Order.class);
                if (order.getTotalAmount().compareTo(MANUAL_REVIEW_THRESHOLD) > 0) {
                    throw new IllegalStateException(String.format(
                        "Order %s total %s exceeds the manual-review threshold (%s) and cannot be auto-dispatched",
                        order.getOrderId(), order.getTotalAmount(), MANUAL_REVIEW_THRESHOLD));
                }
            })
            .process("assign-driver", (ioContext, contribution) ->
                ioContext.setOutputPayload(dispatchService.assignDriver(ioContext.getInputPayloadAs(Order.class))))
            .wireTap("log-dispatched-order", "slf4j://deliveries-dispatched")
            .process("write-delivery-record", (ioContext, contribution) -> {
                final Order order = ioContext.getInputPayloadAs(Order.class);
                deliveryLogWriter.writeRecord(order.getDriverId(), order.toDeliveryLogLine());
            })
            // Composed: retry first (in case validate-order-amount's failure were ever transient),
            // then - once genuinely exhausted - the dead letter channel, not silent drop. Resumed
            // retries jump directly back to validate-order-amount, not the flow's source.
            .withRetryChannel(retry -> retry.maxAttempts(DISPATCH_MAX_ATTEMPTS))
            .withErrorChannel(c -> c.definedFlow(DISPATCH_DEAD_LETTER_FLOW))
            .build();
    }

    /**
     * Where orders land once {@code dispatchProcessingFlow}'s dead letter channel routes them —
     * i.e. every order whose total stayed above {@link #MANUAL_REVIEW_THRESHOLD} through all
     * {@value #DISPATCH_MAX_ATTEMPTS} attempts. {@code dispatchProcessingFlow}'s {@code
     * .definedFlow(DISPATCH_DEAD_LETTER_FLOW)} targets this flow by its own {@code
     * Pipelite.defineFlow(...)} name, not by its {@code fromSource(...)} resource — the two
     * happen to share the same string below for clarity, but {@code definedFlow(...)} would
     * resolve correctly even if they didn't.
     */
    @DefineFlow
    public FlowDefinition dispatchDeadLetterFlow() {
        return Pipelite.defineFlow(DISPATCH_DEAD_LETTER_FLOW)
            .fromSource("dispatch-dead-letter")
            .wireTap("log-rejected-order", "slf4j://orders-rejected")
            .transformPayload("format-rejected-record", payload -> payload.getPayloadAs(Order.class).toRejectedLogLine())
            .toSink(String.format("file://%s", FoodDeliveryPaths.REJECTED_ORDERS_FILE))
            .build();
    }

}
