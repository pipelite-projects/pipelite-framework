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
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.impl.DefaultPipeliteContext;

/**
 * Plain-Java equivalent of the Spring Boot example, with no Spring on the classpath: flows
 * are declared via {@code @FlowConfiguration}/{@code @DefineFlow} instead of {@code @Bean}
 * methods, and dependencies are wired through the native {@code DependencyRegistry} instead
 * of Spring autowiring.
 */
public class Application {

    public static void main(String[] args) {

        final PipeliteContext context = Pipelite.createContext();

        context.registerDependency("greetingService", new GreetingService());
        context.registerFlowConfigurationClass(AcquireFlowConfiguration.class);

        context.start();

    }

}
