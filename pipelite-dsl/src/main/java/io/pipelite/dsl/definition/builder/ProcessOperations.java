package io.pipelite.dsl.definition.builder;

import io.pipelite.dsl.process.PayloadTransformer;
import io.pipelite.dsl.process.Processor;
import io.pipelite.dsl.route.RecipientList;
import io.pipelite.dsl.route.RoutingTable;

import java.util.function.Function;

public interface ProcessOperations extends BuildOperations {

    ProcessOperations process(String name, Processor processor);

    ProcessOperations wireTap(String name, String endpointURL);

    ProcessOperations transformPayload(String name, PayloadTransformer payloadTransformer);

    ProcessOperations filter(String name, String expression);

    BuildOperations toRoute(Function<RouteConfigurator, RoutingTable<?>> dynamicRouteConfigurator);

    //ProcessorDefinitionOperations.SplitterDefinitionOperations split(String name);
    //AggregateDefinitionOperations aggregate(String name);
    //RoutingDefinitionOperations.DynamicRouterOperations dynamicRouter(String name, Router router);

    SinkOperations toSink(String url);

}
