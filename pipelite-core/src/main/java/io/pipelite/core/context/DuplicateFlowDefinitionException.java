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
package io.pipelite.core.context;

/**
 * Thrown by {@link PipeliteContext#registerFlowDefinition} when a flow with the same
 * {@code flowName} is already registered. {@code registerFlowDefinition} is the single
 * point of convergence for both the native scanner and the Spring-aware registration path,
 * so a duplicate name is never silently ignored here.
 */
public class DuplicateFlowDefinitionException extends RuntimeException {

    public DuplicateFlowDefinitionException(String flowName) {
        super(String.format("A FlowDefinition named '%s' is already registered", flowName));
    }

}
