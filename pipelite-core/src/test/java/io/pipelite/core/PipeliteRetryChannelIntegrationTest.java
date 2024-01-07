package io.pipelite.core;

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PipeliteRetryChannelIntegrationTest {

    private PipeliteContext pipeliteContext;

    @Before
    public void setup(){
        pipeliteContext = Pipelite.createContext();
    }

    @Test
    public void givenRetryChannel_whenExceptionIsThrown_thenForwardToRetryChannel(){

        final AtomicInteger counter = new AtomicInteger(0);
        final FlowDefinition testFlow = Pipelite.defineFlow("test-flow")
            .fromSource("ingress")
            .process("throw-error", ((ioContext, contribution) -> {
                if(counter.incrementAndGet() > 10){
                    contribution.stopExecution();
                    return;
                }
                throw new RuntimeException("Programmatic exception");
            }))
            .toSink("end")
            .withRetryChannel()
            .build();

        pipeliteContext.registerFlowDefinition(testFlow);
        pipeliteContext.start();

        final ExchangeFactory exchangeFactory = pipeliteContext.getExchangeFactory();
        pipeliteContext.supplyExchange("ingress", exchangeFactory.createExchange("test-message"));

        Awaitility.await().atMost(60, TimeUnit.SECONDS).until(() -> counter.get() > 10);

    }

    public static class NotSerializablePayload {

    }

}
