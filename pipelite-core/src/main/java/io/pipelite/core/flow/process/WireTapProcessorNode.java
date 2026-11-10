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

import io.pipelite.core.context.internal.InternalPipeliteContext;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.context.internal.DeclaredDestination;
import io.pipelite.core.context.internal.DeclaresDestinations;
import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.exchange.internal.ExchangeFactory;

import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final String endpointURL;

    private InternalPipeliteContext pipeliteContext;
    private ExchangeFactory exchangeFactory;

    WireTapProcessorNode(String endpointURL) {
        super(NO_OP_PROCESSOR);
        this.endpointURL = endpointURL;
    }

    /**
     * The tap inspects the exchange, it is not a step of the flow: a copy that cannot be delivered (issue #34) is logged
     * and the exchange goes on. It is not handed to the flow's exception handler: a retry or an error channel repeats or
     * parks the whole step, and the exchange that is being inspected has done nothing wrong. Without the catch the failure
     * escaped the node raw, because the delivery happens outside the try/catch that {@link AbstractProcessorNode} wraps
     * around its processor.
     */
    @Override
    public void process(ExchangeImpl exchange) {

        try {
            pipeliteContext.supplyExchange(endpointURL, exchangeFactory.copyExchange(exchange));
        } catch (RuntimeException exception) {
            if (sysLogger.isWarnEnabled()) {
                sysLogger.warn("Unable to deliver the copy of the exchange to the wire tap '{}', the flow goes on", endpointURL, exception);
            }
        }
        super.process(exchange);

    }

    @Override
    public Collection<DeclaredDestination> declaredDestinations() {
        return List.of(new DeclaredDestination(endpointURL, "wireTap(...)"));
    }

    @Override
    public void setPipeliteContext(InternalPipeliteContext pipeliteContext) {
        Preconditions.notNull(pipeliteContext, "pipeliteContext is required and cannot be null");
        this.pipeliteContext = pipeliteContext;
        this.exchangeFactory = pipeliteContext.getExchangeFactory();
    }
}
