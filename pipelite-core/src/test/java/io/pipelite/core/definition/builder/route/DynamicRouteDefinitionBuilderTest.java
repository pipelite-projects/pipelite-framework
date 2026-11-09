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

import io.pipelite.core.flow.route.ExpressionConditionEvaluator;
import io.pipelite.dsl.route.ExpressionCondition;
import io.pipelite.dsl.route.RoutingTable;
import io.pipelite.expression.ExpressionParser;
import org.junit.Assert;
import org.junit.Test;

/**
 * Issue #148: {@code otherwise(...)} is optional. A routing table that ends right after a {@code then(...)} is defined,
 * and a message that matches nothing fails as a failed step (see {@code RouterNodeTest}).
 */
public class DynamicRouteDefinitionBuilderTest {

    private DynamicRouteDefinitionBuilder builder() {
        return new DynamicRouteDefinitionBuilder(new ExpressionConditionEvaluator(new ExpressionParser()));
    }

    @Test
    public void givenNoOtherwise_whenTheTableEnds_thenItHasNoDefaultRoutes() {
        final RoutingTable<ExpressionCondition> table = builder()
                .when("Headers['type'] == 'A'").then("queue://a")
                .end();

        Assert.assertFalse(table.getDefaultRoutes().isPresent());
    }

    @Test
    public void givenOtherwise_whenTheTableEnds_thenItHasTheDefaultRoutes() {
        final RoutingTable<ExpressionCondition> table = builder()
                .when("Headers['type'] == 'A'").then("queue://a")
                .otherwise("queue://b", "queue://c")
                .end();

        Assert.assertTrue(table.getDefaultRoutes().isPresent());
    }
}
