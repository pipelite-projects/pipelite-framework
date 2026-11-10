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

import io.pipelite.core.context.PipeliteContextAware;
import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.context.internal.InternalPipeliteContext;
import io.pipelite.core.exchange.internal.DistributedIdentityGeneratorImpl;
import io.pipelite.core.exchange.internal.ExchangeFactory;
import io.pipelite.core.exchange.internal.ExchangeFactoryAware;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.flow.internal.AbstractFlowNode;
import io.pipelite.core.flow.internal.FlowNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Issue #34: the tap inspects the exchange, it is not a step of the flow. A copy that cannot be delivered to the tap
 * destination does not escape the node raw, and does not stop the exchange either: it is logged and the flow goes on,
 * without involving the flow's exception handler (which would retry or park the whole step).
 */
public class WireTapProcessorNodeTest {

    private InternalPipeliteContext pipeliteContext;
    private ExchangeFactory exchangeFactory;
    private AbstractFlowNode subject;
    private FlowNode next;

    @Before
    public void setup() {
        pipeliteContext = Mockito.mock(InternalPipeliteContext.class);
        exchangeFactory = new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));
        Mockito.when(pipeliteContext.getExchangeFactory()).thenReturn(exchangeFactory);

        subject = (AbstractFlowNode) ProcessorNodeFactory.wireTap("slf4j://audit");
        ((PipeliteContextAware) subject).setPipeliteContext(pipeliteContext);
        ((ExchangeFactoryAware) subject).setExchangeFactory(exchangeFactory);
        subject.setFlowName("a-flow");
        subject.setProcessorName("wire-tap");
        next = Mockito.mock(FlowNode.class);
        subject.setNext(next);
    }

    @Test
    public void givenADestinationThatCannotBeDelivered_thenTheExchangeGoesOn() {
        Mockito.doThrow(new IllegalArgumentException("nobody there")).when(pipeliteContext)
                .supplyExchange(Mockito.anyString(), Mockito.any());

        subject.process(exchangeFactory.createExchange("a payload"));

        Mockito.verify(next).process(Mockito.any());
    }

    @Test
    public void givenADestinationThatCannotBeDelivered_thenTheExceptionHandlerIsNotCalled() {
        Mockito.doThrow(new IllegalArgumentException("nobody there")).when(pipeliteContext)
                .supplyExchange(Mockito.anyString(), Mockito.any());
        final AtomicBoolean handled = new AtomicBoolean(false);
        subject.setExceptionHandler((exception, exchange) -> handled.set(true));

        final ExchangeImpl exchange = exchangeFactory.createExchange("a payload");
        subject.process(exchange);

        Assert.assertFalse(handled.get());
        Mockito.verify(next).process(Mockito.any());
    }

    @Test
    public void givenADestinationThatCannotBeDeliveredAndNoExceptionHandler_thenNothingIsThrown() {
        Mockito.doThrow(new IllegalArgumentException("nobody there")).when(pipeliteContext)
                .supplyExchange(Mockito.anyString(), Mockito.any());

        subject.process(exchangeFactory.createExchange("a payload"));
    }

    @Test
    public void givenADestinationThatWorks_thenTheCopyIsDeliveredAndTheExchangeGoesOn() {
        final ExchangeImpl exchange = exchangeFactory.createExchange("a payload");
        subject.process(exchange);

        Mockito.verify(pipeliteContext).supplyExchange(Mockito.eq("slf4j://audit"), Mockito.any());
        Mockito.verify(next).process(Mockito.any());
    }

}
