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
package io.pipelite.core.flow;

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.spi.flow.concurrent.SourceWorkerPoolAware;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;

/**
 * Injects framework-level dependencies ({@code ExchangeFactory}, {@code PipeliteContext}, the
 * shared source worker pool) into a node via its {@code Aware} marker interfaces. Shared between
 * {@link FlowFactory} (top-level flow nodes) and {@code SplitterNode} (inner segment nodes, which
 * are never seen by {@code FlowFactory} and must be injected separately) so the two don't each
 * carry their own copy of the same {@code instanceof} dance.
 */
public final class FlowNodeConfigurer {

    private FlowNodeConfigurer() {
    }

    public static void injectDependencies(Object flowNode, PipeliteContext context) {
        if (flowNode instanceof ExchangeFactoryAware) {
            ((ExchangeFactoryAware) flowNode).setExchangeFactory(context.getExchangeFactory());
        }
        if (flowNode instanceof PipeliteContextAware) {
            ((PipeliteContextAware) flowNode).setPipeliteContext(context);
        }
        if (flowNode instanceof SourceWorkerPoolAware) {
            ((SourceWorkerPoolAware) flowNode).setSourceWorkerPool(context.getSourceWorkerPool());
        }
    }
}
