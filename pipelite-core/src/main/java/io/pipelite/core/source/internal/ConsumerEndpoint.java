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
package io.pipelite.core.source.internal;

import io.pipelite.spi.endpoint.Consumer;

/**
 * An endpoint of the framework's own that provides the consumer of the flow it starts itself, instead of
 * a {@link io.pipelite.spi.source.Source} wrapped by the framework: the {@code queue://} channel, whose
 * exchanges are not received from anywhere but handed over by the producer of another flow, and the
 * retry channel, whose exchanges come from the repository of the executions to retry. Adapters never
 * implement this: it is not part of the SPI.
 */
public interface ConsumerEndpoint {

    Consumer createConsumer();
}
