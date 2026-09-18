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
package io.pipelite.dsl.definition.builder.error;

import io.pipelite.dsl.definition.ErrorChannelDefinition;

/**
 * Renamed from {@code DefinedErrorChannelOperations} (issue #91), following {@link
 * ErrorChannelOperations#toChannel}'s own rename from {@code definedFlow(...)}. Extends {@link
 * ErrorChannelDefinition} so a call chain like {@code c -> c.toChannel(target)} can be returned
 * directly from an {@code ErrorChannelConfigurator} lambda.
 */
public interface ChannelErrorChannelOperations extends ErrorChannelDefinition {
}
