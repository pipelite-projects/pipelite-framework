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
package io.pipelite.components.time;

import io.pipelite.spi.endpoint.PollingSourceConfigurer;

/**
 * Time has no adapter-specific settings of its own — every configurable concern
 * ({@code initialDelay}/{@code period}/{@code timeUnit}/{@code batchSize}) is already generic
 * {@link PollingSourceConfigurer} territory. Still its own named, empty subclass (not used
 * directly) so a mismatched configurer for this adapter still fails via the usual {@code
 * ClassCastException} safety net - see {@link PollingSourceConfigurer}'s own Javadoc for why.
 */
public final class TimeSourceConfigurer extends PollingSourceConfigurer {
}
