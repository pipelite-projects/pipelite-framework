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

import io.pipelite.dsl.definition.SourceConfigurer;

import java.util.function.Consumer;

public interface FlowOperations extends SourceOperations, ProcessOperations,
    SinkOperations, BuildOperations {

    SourceOperations fromSource(String url);

    /**
     * Typed alternative to {@code EndpointURL} query-string parameters for configuring this
     * source - see {@link SourceConfigurer}'s own Javadoc for the full rationale. {@code
     * configurer} is a lambda whose single declared parameter type is the adapter-specific
     * concrete configurer (e.g. {@code (KafkaSourceConfigurer c) -> c.groupId("orders-group")}),
     * constructed by the framework once the endpoint's real adapter is resolved at {@code
     * PipeliteContext#start()} - never eagerly here.
     */
    <C extends SourceConfigurer> SourceOperations fromSource(String url, Consumer<C> configurer);

}
