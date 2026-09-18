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

import io.pipelite.dsl.process.ExceptionHandler;

/**
 * Three mutually-exclusive, terminal entry points (issue #91) - each returns {@link
 * EndOperations} (only {@code .build()} next), so none can be chained with another. This makes a
 * custom {@code ExceptionHandler} configured alongside retry/error-channel structurally
 * impossible to write, rather than silently ignored at runtime. See
 * io.pipelite.core.definition.builder.FlowDefinitionBuilder#resolveExceptionHandler for how each
 * one resolves into a single {@code ExceptionHandler}.
 */
public interface BuildOperations extends EndOperations {

    EndOperations withRetry(RetryConfigurator configurator);
    EndOperations withErrorChannel(ErrorChannelConfigurator configurator);
    EndOperations withExceptionHandler(ExceptionHandler exceptionHandler);

}
