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
package io.pipelite.test.support.matchers;

import io.pipelite.core.config.DefaultDependencyRegistry;
import io.pipelite.core.config.FlowConfigurationScanner;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.test.PipeliteTestFixture;

/**
 * Factory for the built-in {@link Precondition}s usable with
 * {@link PipeliteTestFixture#given(Precondition...)}.
 */
public final class Preconditions {

    private Preconditions() {
    }

    public static Precondition header(String name, Object value) {
        return target -> target.header(name, value);
    }

    public static Precondition inputPayload(Object payload) {
        return target -> target.payload(payload);
    }

    /**
     * Registers a flow definition to be deployed in the test context.
     * Multiple flows can be chained with {@code link://} between them.
     */
    public static Precondition flowDefinition(FlowDefinition flowDefinition) {
        return target -> target.flowDefinition(flowDefinition);
    }

    /**
     * Scans a {@code @FlowConfiguration} class (no-arg constructor, no dependencies)
     * and registers each discovered flow definition in the test context.
     */
    public static Precondition flowConfiguration(Class<?> configClass) {
        return flowConfiguration(configClass, new Object[0]);
    }

    /**
     * Scans a {@code @FlowConfiguration} class and registers each discovered flow
     * definition in the test context. Each element of {@code dependencies} is
     * registered in a {@link DefaultDependencyRegistry} by its runtime type, so
     * {@code @DefineFlow} method parameters are resolved by type — the same
     * contract as the plain-Java SPI scanner.
     */
    public static Precondition flowConfiguration(Class<?> configClass, Object... dependencies) {
        return target -> {
            final DefaultDependencyRegistry registry = new DefaultDependencyRegistry();
            for (Object dep : dependencies) {
                registry.register(dep.getClass().getName(), dep);
            }
            final FlowConfigurationScanner scanner = new FlowConfigurationScanner();
            for (FlowDefinition flowDefinition : scanner.scan(configClass, registry)) {
                target.flowDefinition(flowDefinition);
            }
        };
    }

    /**
     * Overrides the default capture timeout (5 s) used by {@code Actions.supplyTo(...)}.
     * Reduce this when testing flows that intentionally stop or filter messages
     * before reaching the {@code test://} sink.
     */
    public static Precondition timeout(long seconds) {
        return target -> target.timeout(seconds);
    }
}
