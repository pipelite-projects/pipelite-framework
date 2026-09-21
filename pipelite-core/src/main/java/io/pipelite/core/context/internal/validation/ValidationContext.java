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
package io.pipelite.core.context.internal.validation;

import io.pipelite.dsl.definition.FlowDefinition;

import java.util.Collection;

/**
 * What a {@link ContextValidator} may look at: a read-only view of the context as it is right
 * before anything is started, kept narrow on purpose so a validator never depends on the internals
 * of {@code DefaultPipeliteContext}.
 */
public interface ValidationContext {

    /**
     * Every flow registered so far, in registration order.
     */
    Collection<FlowDefinition> flowDefinitions();

    /**
     * An endpoint URL with its {@code ${...}} placeholders resolved, exactly as the endpoint
     * factory resolves it when it builds the endpoint.
     *
     * @throws RuntimeException if a placeholder cannot be resolved
     */
    String resolveURL(String rawURL);

}
