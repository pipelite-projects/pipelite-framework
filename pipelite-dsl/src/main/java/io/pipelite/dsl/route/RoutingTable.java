package io.pipelite.dsl.route;

import io.pipelite.common.support.Preconditions;
import io.pipelite.dsl.IOContext;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class RoutingTable<V extends Condition> extends CopyOnWriteArrayList<RouteEntry<V>> {

    private final ConditionEvaluator routeConditionEvaluator;

    private RecipientList defaultRoutes;

    public RoutingTable(ConditionEvaluator routeConditionEvaluator){
        Objects.requireNonNull(routeConditionEvaluator, "routeConditionEvaluator is required and cannot be null");
        this.routeConditionEvaluator = routeConditionEvaluator;
    }

    public void setDefaultRoutes(String... defaultRoutes) {
        Preconditions.state(defaultRoutes.length > 0, "At least one default route is required");
        this.defaultRoutes = RecipientList.of(defaultRoutes);
    }

    public Optional<RecipientList> resolveRoute(IOContext ioContext){

        for(RouteEntry<V> routeEntry : this){

            final Condition routeCondition = routeEntry.getCondition();
            boolean isSatisfied = routeConditionEvaluator.evaluate(routeCondition, ioContext);
            if(isSatisfied){
                return Optional.of(routeEntry.getDestination());
            }
        }

        return Optional.ofNullable(defaultRoutes);

    }
}
