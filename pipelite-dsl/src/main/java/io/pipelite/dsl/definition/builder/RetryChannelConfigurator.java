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

import io.pipelite.dsl.definition.builder.retry.RetryChannelOperations;

/**
 * Consumer-style on purpose (no return value), unlike {@link ErrorChannelConfigurator}: avoids
 * the class of bug fixed on {@code DefinedErrorChannelOperations}, where a fluent method's
 * return type didn't match what the configurator contract required callers to hand back.
 */
public interface RetryChannelConfigurator {

    void configure(RetryChannelOperations builder);

}
