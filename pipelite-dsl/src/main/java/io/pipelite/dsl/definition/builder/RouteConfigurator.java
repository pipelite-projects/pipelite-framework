package io.pipelite.dsl.definition.builder;

import io.pipelite.dsl.definition.builder.route.ContentBasedRouteOperations;
import io.pipelite.dsl.definition.builder.route.DynamicRouteOperations;
import io.pipelite.dsl.definition.builder.route.StaticRouteOperations;

public interface RouteConfigurator {

    StaticRouteOperations staticRouting();

    DynamicRouteOperations dynamicRouting();

    ContentBasedRouteOperations contentBasedRouting();

}
