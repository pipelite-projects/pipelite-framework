package io.pipelite.core.definition.builder.route;

import io.pipelite.common.support.Preconditions;
import io.pipelite.dsl.definition.builder.RouteConfigurator;
import io.pipelite.dsl.definition.builder.route.ContentBasedRouteOperations;
import io.pipelite.dsl.definition.builder.route.DynamicRouteOperations;
import io.pipelite.dsl.definition.builder.route.StaticRouteOperations;
import io.pipelite.dsl.route.*;

public class RouteDefinitionBuilder implements RouteConfigurator {

    private final ConditionEvaluator routeConditionEvaluator;

    public RouteDefinitionBuilder(ConditionEvaluator routeConditionEvaluator) {
        Preconditions.notNull(routeConditionEvaluator, "routeConditionEvaluator is required and cannot be null");
        this.routeConditionEvaluator = routeConditionEvaluator;
    }

    @Override
    public StaticRouteOperations staticRouting() {
        return new StaticRouteDefinitionBuilder(routeConditionEvaluator);
    }

    @Override
    public DynamicRouteOperations dynamicRouting() {
        return new DynamicRouteDefinitionBuilder(routeConditionEvaluator);
    }

    @Override
    public ContentBasedRouteOperations contentBasedRouting() {
        throw new UnsupportedOperationException();
    }

}
