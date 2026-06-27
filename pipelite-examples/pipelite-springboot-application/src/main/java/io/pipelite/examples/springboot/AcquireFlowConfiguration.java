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
package io.pipelite.examples.springboot;

import io.pipelite.core.Pipelite;
import io.pipelite.dsl.annotation.DefineFlow;
import io.pipelite.dsl.annotation.FlowConfiguration;
import io.pipelite.dsl.definition.FlowDefinition;

/**
 * Flow definitions for this application.
 *
 * <p>Declared via {@code @FlowConfiguration} / {@code @DefineFlow} and registered through
 * {@code @EnablePipelite(flowConfigurations = AcquireFlowConfiguration.class)}: Spring
 * constructs this class as a regular bean (constructor injection applies), then
 * {@code FlowConfigurationBeanPostProcessor} invokes each {@code @DefineFlow} method resolving
 * parameters from the Spring {@code ApplicationContext} via {@code SpringDependencyRegistry}.
 *
 * <p>The same {@code @FlowConfiguration} / {@code @DefineFlow} style is used in the plain-Java
 * sibling example ({@code pipelite-simple-application}); only the DI mechanism differs.
 */
@FlowConfiguration
public class AcquireFlowConfiguration {

    @DefineFlow
    public FlowDefinition acquireFlow(GreetingService greetingService) {
        return Pipelite.defineFlow("acquire-flow")
            .fromSource("http://ingress")
            .transformPayload("greet-sender", payloadHolder ->
                greetingService.greet(payloadHolder.getPayloadAs(String.class)))
            .wireTap("wiretap-logger", "slf4j://wire-tap-logger")
            .toSink("slf4j://main-logger")
            .build();
    }

}
