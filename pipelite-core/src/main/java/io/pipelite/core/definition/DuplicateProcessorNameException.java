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
package io.pipelite.core.definition;

/**
 * Thrown by {@link FlowDefinitionImpl#addProcessorDefinition} when a step name is reused
 * within the same flow. Required for correctness by name-based node lookup (see
 * {@code io.pipelite.core.flow.FlowNodeLocator}, used to resume a flow directly at its failed
 * step) — a duplicate name would otherwise resolve to whichever step was added first,
 * silently.
 */
public class DuplicateProcessorNameException extends RuntimeException {

    public DuplicateProcessorNameException(String flowName, String processorName) {
        super(String.format("A processor named '%s' is already defined on flow '%s'", processorName, flowName));
    }

}
