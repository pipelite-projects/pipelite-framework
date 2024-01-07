package io.pipelite.dsl.definition.builder.route;

public interface ContentBasedRouteOperations {

    ConditionalExpressionOperations when(Class<?> payloadType);

    interface ConditionalExpressionOperations {
        ThenOperations then(String routeName);
    }

    interface ThenOperations extends ContentBasedRouteOperations {
        void otherwise(String routeName);
    }

}
