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
package io.pipelite.core.definition.builder.route;

import io.pipelite.common.support.Preconditions;
import io.pipelite.dsl.definition.builder.RouteConfigurator;
import io.pipelite.dsl.definition.builder.route.ContentBasedRouteOperations;
import io.pipelite.dsl.definition.builder.route.DynamicRouteOperations;
import io.pipelite.dsl.route.ConditionEvaluator;

public class RouteDefinitionBuilder implements RouteConfigurator {

    private final ConditionEvaluator routeConditionEvaluator;

    public RouteDefinitionBuilder(ConditionEvaluator routeConditionEvaluator) {
        Preconditions.notNull(routeConditionEvaluator, "routeConditionEvaluator is required and cannot be null");
        this.routeConditionEvaluator = routeConditionEvaluator;
    }

    @Override
    public DynamicRouteOperations dynamic() {
        return new DynamicRouteDefinitionBuilder(routeConditionEvaluator);
    }

    @Override
    public ContentBasedRouteOperations contentBased() {
        throw new UnsupportedOperationException();
    }

}
