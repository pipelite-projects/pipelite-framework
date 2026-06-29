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
package io.pipelite.examples.simple;

import io.pipelite.core.Pipelite;
import io.pipelite.dsl.annotation.DefineFlow;
import io.pipelite.dsl.annotation.FlowConfiguration;
import io.pipelite.dsl.definition.FlowDefinition;

/**
 * Production flow definitions for this application, discoverable via the native
 * convention-over-configuration SPI: {@code @FlowConfiguration} marks this class as a
 * container of {@code @DefineFlow} factory methods, scanned by
 * {@code PipeliteContext.registerFlowConfigurationClass}.
 *
 * <p>Unlike the Spring Boot example (where {@code FlowDefinition} beans are intercepted by
 * {@code FlowDefinitionRegistrar}), no DI container is involved here: {@code greetingService}
 * is resolved from the context's {@code DependencyRegistry}, populated beforehand via
 * {@code PipeliteContext.registerDependency}.
 */
@FlowConfiguration
public class AcquireFlowConfiguration {

    @DefineFlow
    public FlowDefinition acquireFlow(GreetingService greetingService) {
        return Pipelite.defineFlow("acquire-flow")
            .fromSource("http://ingress")
            .transformPayload("greet-sender", payloadHolder -> greetingService.greet(payloadHolder.getPayloadAs(String.class)))
            .wireTap("wiretap-logger", "slf4j://wire-tap-logger")
            .toSink("slf4j://main-logger")
            .build();
    }

}
