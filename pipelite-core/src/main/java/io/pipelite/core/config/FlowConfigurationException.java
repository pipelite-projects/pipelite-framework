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
package io.pipelite.core.config;

/**
 * Thrown for structural errors found while scanning a {@code @FlowConfiguration} class:
 * the class is not annotated, cannot be instantiated via its default constructor, or a
 * {@code @DefineFlow} method does not return a {@code FlowDefinition}.
 */
public class FlowConfigurationException extends RuntimeException {

    public FlowConfigurationException(String message) {
        super(message);
    }

    public FlowConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

}
