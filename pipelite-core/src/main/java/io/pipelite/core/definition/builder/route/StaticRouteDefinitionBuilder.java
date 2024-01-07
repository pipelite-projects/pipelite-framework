package io.pipelite.core.definition.builder.route;

import io.pipelite.common.support.Preconditions;
import io.pipelite.dsl.definition.builder.route.StaticRouteOperations;
import io.pipelite.dsl.route.ConditionEvaluator;
import io.pipelite.dsl.route.ExpressionCondition;
import io.pipelite.dsl.route.RoutingTable;

public class StaticRouteDefinitionBuilder implements StaticRouteOperations, StaticRouteOperations.EndOperations {

    private final ConditionEvaluator routeConditionEvaluator;

    private String[] defaultRoutes;

    public StaticRouteDefinitionBuilder(ConditionEvaluator routeConditionEvaluator){
        this.routeConditionEvaluator = Preconditions.notNull(routeConditionEvaluator, "routeConditionEvaluator is required and cannot be null");
    }

    @Override
    public EndOperations recipientList(String... destinations) {
        this.defaultRoutes = destinations;
        return this;
    }

    @Override
    public RoutingTable<?> end() {
        final RoutingTable<ExpressionCondition> routingTable = new RoutingTable<>(routeConditionEvaluator);
        routingTable.setDefaultRoutes(defaultRoutes);
        return routingTable;
    }

}
