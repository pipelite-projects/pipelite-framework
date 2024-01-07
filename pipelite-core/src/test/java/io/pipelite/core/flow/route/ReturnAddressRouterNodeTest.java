package io.pipelite.core.flow.route;

import io.pipelite.core.Pipelite;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReturnAddressRouterNodeTest {

    private PipeliteContext context;

    @Before
    public void setup(){
        context = new DefaultPipeliteContext();
    }

    @Test
    public void shouldReplyToReturnAddress(){

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();
        final AtomicBoolean replayedToDestination = new AtomicBoolean(false);

        context.registerFlowDefinition(
            Pipelite.defineFlow("sender-flow")
                .fromSource("sender-start")
                .process("process", (ioContext, contribution) -> {})
                .build());

        context.registerFlowDefinition(
            Pipelite.defineFlow("recipient-flow")
                .fromSource("recipient-start")
                .process("process", (ioContext, contribution) -> replayedToDestination.set(true))
                .build());

        context.start();

        final Exchange exchange = exchangeFactory.createExchange("Hello Pipelite!");
        exchange.setReturnAddress("recipient-start");

        context.supplyExchange("sender-start", exchange);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(replayedToDestination::get);
    }
}
