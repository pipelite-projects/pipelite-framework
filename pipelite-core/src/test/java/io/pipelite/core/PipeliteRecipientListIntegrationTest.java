package io.pipelite.core;

import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PipeliteRecipientListIntegrationTest {

    private PipeliteContext context;

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
    }

    @Test
    public void shouldForwardToRecipients(){

        final AtomicInteger forwardedCount = new AtomicInteger(0);

        final FlowDefinition origin = Pipelite.defineFlow("origin-flow")
            .fromSource("origin-start")
            .toRoute(routeConfigurator ->
                routeConfigurator.dynamicRouting()
                    .when("Headers['X-To-Destinations'] eq 'true'").then("link://destination-01-start", "link://destination-02-start")
                    .otherwise("slf4j://logger")
                    .end()
            )
            .build();

        final FlowDefinition destination01 = Pipelite.defineFlow("destination-01-flow")
            .fromSource("destination-01-start")
            .process("process-message", (ioContext, contribution) -> forwardedCount.incrementAndGet())
            .build();

        final FlowDefinition destination02 = Pipelite.defineFlow("destination-02-flow")
            .fromSource("destination-02-start")
            .process("process-message", (ioContext, contribution) -> forwardedCount.incrementAndGet())
            .build();

        context.registerFlowDefinition(origin);
        context.registerFlowDefinition(destination01);
        context.registerFlowDefinition(destination02);
        context.start();

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();

        final Exchange exchange = exchangeFactory.createExchange("Hello Pipelite!");
        exchange.putHeader("X-To-Destinations", "true");

        context.supplyExchange("origin-start", exchange);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> forwardedCount.get() == 2);

    }

}
