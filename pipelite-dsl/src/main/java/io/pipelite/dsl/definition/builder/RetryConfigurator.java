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

import io.pipelite.dsl.definition.builder.retry.RetryOperations;
import io.pipelite.dsl.definition.builder.retry.RetryTerminalOperations;

/**
 * Renamed from {@code RetryChannelConfigurator} (issue #91). Unlike before, the lambda must now
 * return a {@link RetryTerminalOperations} - obtainable only by calling {@code onErrorChannel(...)}/
 * {@code onExceptionHandler(...)} on the given {@link RetryOperations} - so a retry with no
 * exhaustion action ({@code retry -> retry.maxAttempts(3)} alone) no longer type-checks.
 */
public interface RetryConfigurator {

    RetryTerminalOperations configure(RetryOperations retry);

}
