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
package io.pipelite.core.flow.route;

import io.pipelite.core.flow.expression.TextExpressionEvaluator;
import io.pipelite.dsl.route.ConditionEvaluator;
import io.pipelite.dsl.route.RecipientList;
import io.pipelite.dsl.route.RoutingTable;
import io.pipelite.spi.flow.exchange.FlowNode;

/**
 * The only public entry point for building nodes backed by this package's {@code FlowNode}
 * implementations - part of the pre-v1.0.0 audit's Tier 4 #12 lock-down (issue #82).
 * {@code RouterNode}/{@code RecipientListRouterNode} are package-private: every caller outside
 * this package only ever needed a {@link FlowNode} back, never the concrete type.
 */
public final class RouteNodeFactory {

    private RouteNodeFactory() {
    }

    public static FlowNode router(RoutingTable<?> routingTable, TextExpressionEvaluator textExpressionEvaluator) {
        return new RouterNode(routingTable, textExpressionEvaluator);
    }

    public static FlowNode recipientList(RecipientList recipientList, ConditionEvaluator conditionEvaluator) {
        return new RecipientListRouterNode(recipientList, conditionEvaluator);
    }

    /**
     * Follows a routing slip in front of a flow's exit (the producer of a {@code toSink(...)}, or
     * a {@link FlowExitNode}): the exit only runs when there is no slip route left to take.
     */
    public static FlowNode routingSlipGate() {
        return new RoutingSlipGateNode(false);
    }

    /**
     * The end of a flow with no exit of its own: follows a routing slip, and only once it is
     * exhausted (or never set) replies to the exchange's return address.
     */
    public static FlowNode endOfFlowGate() {
        return new RoutingSlipGateNode(true);
    }

    public static String routingSlipGateName() {
        return RoutingSlipGateNode.PROCESSOR_NAME;
    }

}
