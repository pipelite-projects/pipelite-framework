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
package io.pipelite.core.flow.process;

import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.flow.exchange.FlowNode;

/**
 * The only public entry point for building nodes backed by this package's {@code FlowNode}
 * implementations - part of the pre-v1.0.0 audit's Tier 4 #12 lock-down (issue #82).
 * {@code DefaultProcessorNode}/{@code WireTapProcessorNode} are package-private: every caller
 * outside this package only ever needed a {@link FlowNode} back, never the concrete type, so
 * hiding them costs nothing.
 */
public final class ProcessorNodeFactory {

    private ProcessorNodeFactory() {
    }

    public static FlowNode wrap(Processor delegate) {
        return new DefaultProcessorNode(delegate);
    }

    public static FlowNode wireTap(String endpointURL) {
        return new WireTapProcessorNode(endpointURL);
    }

}
