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
package io.pipelite.channels.kafka;

import io.pipelite.dsl.definition.SinkConfigurer;
import io.pipelite.dsl.headers.HeaderPropagation;
import io.pipelite.dsl.headers.PropagatesHeaders;

/**
 * The typed settings of a {@code kafka://} sink: {@code toSink("kafka://orders", (KafkaSinkConfigurer c) ->
 * c.propagateHeaders(h -> h.filter("Idempotency-Key")))}. Which headers of the exchange are written as headers of the
 * record; none by default.
 */
public final class KafkaSinkConfigurer implements SinkConfigurer, PropagatesHeaders {

    private HeaderPropagation headerPropagation = HeaderPropagation.none();

    @Override
    public HeaderPropagation headerPropagation() {
        return headerPropagation;
    }

    @Override
    public void setHeaderPropagation(HeaderPropagation propagation) {
        this.headerPropagation = propagation;
    }
}
