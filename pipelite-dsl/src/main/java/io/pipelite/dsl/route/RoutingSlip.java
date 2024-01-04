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
