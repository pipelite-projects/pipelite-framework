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
package io.pipelite.dsl.definition.builder;

import io.pipelite.dsl.process.PayloadTransformer;
import io.pipelite.dsl.process.Processor;
import io.pipelite.dsl.route.RecipientList;
import io.pipelite.dsl.route.RoutingTable;
import io.pipelite.dsl.barrier.BarrierConfigurator;
import io.pipelite.dsl.barrier.BarrierOperations;
import io.pipelite.dsl.split.SplitConfigurator;

import java.util.function.Function;

public interface ProcessOperations extends BuildOperations {

    ProcessOperations process(String name, Processor processor);

    ProcessOperations wireTap(String name, String endpointURL);

    ProcessOperations transformPayload(String name, PayloadTransformer payloadTransformer);

    ProcessOperations filter(String name, String expression);

    @Deprecated
    BuildOperations toRecipientList(RecipientListConfigurator recipientListConfigurator);

    BuildOperations toRoute(Function<RouteConfigurator, RoutingTable<?>> dynamicRouteConfigurator);

    ProcessOperations split(String name, SplitConfigurator segmentConfigurator);

    /**
     * Makes the exchange that reaches this step wait until the executions of other flows have completed, and then
     * lets it go on unchanged. The flows are awaited by the URL of the queue they read, and the exchange resumes on
     * the workers of its own flow. See {@link BarrierOperations}.
     */
    ProcessOperations barrier(String name, BarrierConfigurator barrierConfigurator);

    SinkOperations toSink(String url);

}
