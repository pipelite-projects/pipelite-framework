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
