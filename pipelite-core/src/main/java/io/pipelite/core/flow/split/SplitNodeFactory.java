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
package io.pipelite.core.flow.split;

import io.pipelite.dsl.split.SplitSegment;
import io.pipelite.spi.flow.exchange.FlowNode;

/**
 * The only public entry point for building a node backed by this package's {@code FlowNode}
 * implementation - part of the pre-v1.0.0 audit's Tier 4 #12 lock-down (issue #82).
 * {@code SplitterNode} is package-private: {@code FlowDefinitionBuilder#split(...)} only ever
 * needed a {@link FlowNode} back, never the concrete type.
 */
public final class SplitNodeFactory {

    private SplitNodeFactory() {
    }

    public static FlowNode splitter(SplitSegment segment) {
        return new SplitterNode(segment);
    }

}
