package io.pipelite.dsl.definition.builder.route;

import io.pipelite.dsl.route.ExpressionCondition;
import io.pipelite.dsl.route.RoutingTable;

public interface DynamicRouteOperations {

    ConditionalExpressionOperations when(String expression);
    EndOperations defaultRoute(String destination);

    interface ConditionalExpressionOperations {
        ThenOperations then(String... destinations);
    }

    interface ThenOperations extends DynamicRouteOperations, EndOperations{

        EndOperations otherwise(String destination);

    }

    interface EndOperations {
        RoutingTable<ExpressionCondition> end();
    }
}
