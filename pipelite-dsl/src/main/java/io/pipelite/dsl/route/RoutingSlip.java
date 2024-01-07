package io.pipelite.dsl.route;

import io.pipelite.common.support.Preconditions;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

public class RoutingSlip {

    private final Queue<String> routes;

    public static RoutingSlip create(String... routes){
        return new RoutingSlip(Arrays.asList(routes));
    }

    public RoutingSlip(Collection<String> routes){
        Preconditions.state(routes != null && !routes.isEmpty(), "Provide at least one route!");
        this.routes = new LinkedList<>(routes);
    }

    public String nextRoute(){
        return routes.poll();
    }

    public boolean hasNext(){
        return !routes.isEmpty();
    }

}
