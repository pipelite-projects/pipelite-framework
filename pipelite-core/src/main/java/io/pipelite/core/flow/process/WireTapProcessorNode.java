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

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.context.internal.DeclaredDestination;
import io.pipelite.core.context.internal.DeclaresDestinations;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;

import java.util.Collection;
import java.util.List;

/**
 * Package-private since #82: construct via {@link ProcessorNodeFactory#wireTap(String)}.
 */
class WireTapProcessorNode extends AbstractProcessorNode implements PipeliteContextAware, DeclaresDestinations {

    private static final class NoOpProcessor implements Processor {
        @Override
        public void process(Exchange exchange, ProcessContribution contribution) {
        }
    }

    private static final NoOpProcessor NO_OP_PROCESSOR = new NoOpProcessor();

    private final String endpointURL;

    private PipeliteContext pipeliteContext;
    private ExchangeFactory exchangeFactory;

    WireTapProcessorNode(String endpointURL) {
        super(NO_OP_PROCESSOR);
        this.endpointURL = endpointURL;
    }

    @Override
    public void process(ExchangeImpl exchange) {

        pipeliteContext.supplyExchange(endpointURL, exchangeFactory.copyExchange(exchange));
        super.process(exchange);

    }

    @Override
    public Collection<DeclaredDestination> declaredDestinations() {
        return List.of(new DeclaredDestination(endpointURL, "wireTap(...)"));
    }

    @Override
    public void setPipeliteContext(PipeliteContext pipeliteContext) {
        Preconditions.notNull(pipeliteContext, "pipeliteContext is required and cannot be null");
        this.pipeliteContext = pipeliteContext;
        this.exchangeFactory = pipeliteContext.getExchangeFactory();
    }
}
