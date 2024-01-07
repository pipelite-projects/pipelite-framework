package io.pipelite.dsl.definition.builder;

import io.pipelite.dsl.definition.builder.route.StaticRouteOperations;
import io.pipelite.dsl.route.RecipientList;

public interface RecipientListConfigurator {

    RecipientList configure(StaticRouteOperations recipientListBuilder);

}
