package io.pipelite.dsl.definition.builder.route;

import io.pipelite.dsl.route.RoutingTable;

public interface StaticRouteOperations {

    EndOperations recipientList(String... destinations);

    interface EndOperations {

        RoutingTable<?> end();

    }

}
