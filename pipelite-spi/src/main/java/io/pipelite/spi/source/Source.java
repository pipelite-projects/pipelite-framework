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
package io.pipelite.spi.source;

/**
 * What an adapter implements to feed a flow ({@code fromSource(...)}). There are two shapes: {@link
 * EventSource}, which pushes exchanges when something happens (a broker delivers a record, a request
 * arrives), and {@link PollingSource}, which the framework asks for the next exchange on a schedule.
 * <p>
 * The framework owns everything around the source: the bounded queue, the workers, the backpressure,
 * the durable inbox and the thread naming. The source only receives, and hands the exchanges over
 * through the {@link SourceContext} it is given.
 */
public sealed interface Source permits EventSource, PollingSource {
}
