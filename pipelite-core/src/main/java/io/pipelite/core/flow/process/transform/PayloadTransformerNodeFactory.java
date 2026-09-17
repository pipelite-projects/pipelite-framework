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
package io.pipelite.core.flow.process.transform;

import io.pipelite.dsl.process.PayloadTransformer;
import io.pipelite.dsl.process.Processor;

/**
 * The only public entry point for this package's {@link Processor} implementation - part of the
 * pre-v1.0.0 audit's Tier 4 #12 lock-down (issue #82). {@code PayloadTransformerNode} is
 * package-private: {@code FlowDefinitionBuilder#transformPayload(...)} only ever needed a {@link
 * Processor} back, never the concrete type.
 */
public final class PayloadTransformerNodeFactory {

    private PayloadTransformerNodeFactory() {
    }

    public static Processor create(PayloadTransformer payloadTransformer) {
        return new PayloadTransformerNode(payloadTransformer);
    }

}
