/*
 * Copyright (C) 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
