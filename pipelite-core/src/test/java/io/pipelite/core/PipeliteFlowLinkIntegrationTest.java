package io.pipelite.core;

import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PipeliteFlowLinkIntegrationTest {

    private PipeliteContext context;

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
        //context.registerComponent("link", new LinkComponent());
    }

    @Test
    public void shouldLinkFlows(){

        final AtomicBoolean forwarded = new AtomicBoolean(false);

        final FlowDefinition origin = Pipelite.defineFlow("origin-flow")
            .fromSource("origin-start")
            .toSink("link://destination-start")
            .build();

        final FlowDefinition destination = Pipelite.defineFlow("destination-flow")
            .fromSource("destination-start")
            .process("process-message", (ioContext, contribution) -> forwarded.set(true))
            .build();

        context.registerFlowDefinition(origin);
        context.registerFlowDefinition(destination);
        context.start();

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();
        context.supplyExchange("origin-start", exchangeFactory.createExchange("Hello Pipelite!"));

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(forwarded::get);

        Assert.assertTrue(forwarded.get());

    }

}
