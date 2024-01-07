package io.pipelite.core.definition.builder.route;

import io.pipelite.common.support.Preconditions;
import io.pipelite.dsl.definition.builder.route.DynamicRouteOperations;
import io.pipelite.dsl.route.*;

import java.util.ArrayList;
import java.util.Collection;

public class DynamicRouteDefinitionBuilder implements DynamicRouteOperations,
    DynamicRouteOperations.ConditionalExpressionOperations,
    DynamicRouteOperations.ThenOperations, DynamicRouteOperations.EndOperations {

    private final ConditionEvaluator routeConditionEvaluator;
    private final Collection<RouteEntry<ExpressionCondition>> routes;

    private String defaultRoute;
    private String expression;

    public DynamicRouteDefinitionBuilder(ConditionEvaluator routeConditionEvaluator) {
        this.routeConditionEvaluator = Preconditions.notNull(routeConditionEvaluator, "routeConditionEvaluator is required and cannot be null");
        routes = new ArrayList<>();
    }

    @Override
    public ConditionalExpressionOperations when(String expression) {
        this.expression = expression;
        return this;
    }

    @Override
    public EndOperations defaultRoute(String destination) {
        this.defaultRoute = destination;
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
    public EndOperations otherwise(String destination) {
        this.defaultRoute = destination;
        return this;
    }

    @Override
    public RoutingTable<ExpressionCondition> end() {
        final RoutingTable<ExpressionCondition> routingTable = new RoutingTable<>(routeConditionEvaluator);
        routingTable.addAll(routes);
        routingTable.setDefaultRoutes(defaultRoute);
        return routingTable;
    }
}
