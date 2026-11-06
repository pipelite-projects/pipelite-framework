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
package io.pipelite.dsl.definition;

/**
 * The typed settings of a sink, handed to the callback of {@code toSink(url, configurer)}: {@code toSink("kafka://orders",
 * (KafkaSinkConfigurer c) -> c.propagateHeaders(h -> h.filter("Idempotency-Key")))}.
 * <p>
 * A marker: what a sink can be configured with is a set of capabilities, each an interface a concrete configurer
 * implements (see {@code PropagatesHeaders}), so that a configurer offers exactly the ones its endpoint supports. It is
 * constructed by the adapter, with {@code ChannelAdapter#newSinkConfigurer()}, and handed to the callback; an adapter that
 * returns none does not support one. The counterpart of {@link SourceConfigurer} on the other side of a flow.
 */
public interface SinkConfigurer {
}
