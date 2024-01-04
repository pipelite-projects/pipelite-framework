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
package io.pipelite.core.definition.builder.route;

import io.pipelite.common.support.Preconditions;
import io.pipelite.dsl.definition.builder.route.DynamicRouteOperations;
import io.pipelite.dsl.route.*;

import java.util.*;

public class DynamicRouteDefinitionBuilder implements DynamicRouteOperations,
    DynamicRouteOperations.ConditionalExpressionOperations,
    DynamicRouteOperations.ThenOperations, DynamicRouteOperations.EndOperations {

    private final ConditionEvaluator routeConditionEvaluator;
    private final Collection<RouteEntry<ExpressionCondition>> routes;

    private String[] defaultRoutes;
    private String expression;

    public DynamicRouteDefinitionBuilder(ConditionEvaluator routeConditionEvaluator) {
        Objects.requireNonNull(routeConditionEvaluator, "routeConditionEvaluator is required and cannot be null");
        this.routeConditionEvaluator = routeConditionEvaluator;
        routes = new ArrayList<>();
    }

    @Override
    public ConditionalExpressionOperations when(String expression) {
        this.expression = expression;
        return this;
    }

    @Override
    public ThenOperations then(String... destinations) {
        Preconditions.state(destinations.length > 0, "Almost one destination is required");
        routes.add(new RouteEntry<>(RecipientList.of(destinations),
            new ExpressionCondition(expression)));
        return this;
    }

    @Override
    public EndOperations otherwise(String... destinations) {
        this.defaultRoutes = destinations;
        return this;
    }

    @Override
    public RoutingTable<ExpressionCondition> end() {
        final RoutingTable<ExpressionCondition> routingTable = new RoutingTable<>(routeConditionEvaluator);
        routingTable.addAll(routes);
        routingTable.setDefaultRoutes(defaultRoutes);
        return routingTable;
    }
}
