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

import io.pipelite.dsl.definition.SinkConfigurer;

import java.util.function.Consumer;
import java.util.function.Function;

public interface ProcessOperations extends BuildOperations {

    ProcessOperations process(String name, Processor processor);

    ProcessOperations wireTap(String name, String endpointURL);

    ProcessOperations transformPayload(String name, PayloadTransformer payloadTransformer);

    ProcessOperations filter(String name, String expression);

    /**
     * Hands a copy of the exchange to <em>every</em> one of the recipients, a fixed set of destinations (the
     * Recipient List pattern), and the flow ends here. Each recipient is a URL, as for {@code toSink(...)}. To choose
     * the destinations by looking at the message use {@link #toRoute(Function)}.
     *
     * @throws IllegalArgumentException if no recipient is given, or one is not a URL
     */
    BuildOperations toRecipientList(String... recipients);

    BuildOperations toRoute(Function<RouteConfigurator, RoutingTable<?>> dynamicRouteConfigurator);

    /**
     * Splits a {@code Collection} payload into one exchange per element, and sends each of them on through the steps that
     * follow, on its own: to a recipient list, a route, a sink or any step. Nothing puts the elements back together
     * unless an {@code aggregate} step does. The elements go one after the other, and each carries the headers of
     * {@link io.pipelite.dsl.split.SplitHeaders}.
     */
    ProcessOperations split(String name);

    /**
     * Makes the exchange that reaches this step wait until the executions of other flows have completed, and then
     * lets it go on unchanged. The flows are awaited by the URL of the queue they read, and the exchange resumes on
     * the workers of its own flow. See {@link BarrierOperations}.
     */
    ProcessOperations barrier(String name, BarrierConfigurator barrierConfigurator);

    SinkOperations toSink(String url);

    /**
     * Like {@link #toSink(String)}, with the typed settings of the sink, the counterpart of {@code fromSource(url,
     * configurer)}: {@code toSink("kafka://orders", (KafkaSinkConfigurer c) -> c.propagateHeaders(h ->
     * h.filter("Idempotency-Key")))}. The type of the configurer is the one of the adapter of the URL, and an adapter that
     * has no settings for a sink does not accept one: both are checked when the context starts. See {@link
     * SinkConfigurer}.
     */
    <C extends SinkConfigurer> SinkOperations toSink(String url, Consumer<C> configurer);

}
