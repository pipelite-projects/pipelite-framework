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

import io.pipelite.core.Pipelite;
import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.context.Service;
import io.pipelite.spi.flow.Flow;
import io.pipelite.spi.flow.exchange.*;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FlowFactoryTest {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private PipeliteContext ctx;
    private FlowFactory subject;

    @Before
    public void setup(){
        ctx = new DefaultPipeliteContext();
        subject = new FlowFactory(ctx);
    }

    @Test
    public void whenCreateFlow_thenReturnFlowInstance_andExecuteProcessors(){

        final AtomicBoolean targetProcessorInvoked = new AtomicBoolean(false);

        final FlowDefinition flowDefinition = Pipelite.defineFlow("simple-flow")
            .fromSource("start")
            .transformPayload("transform-payload",
                inputPayload ->
                    String.format("This is transformed payload, original is '%s'", inputPayload.getPayloadAs(String.class)))
            .process("print-payload", (ioContext, contribution) -> {
                final String inputPayload = ioContext.getInputPayloadAs(String.class);
                targetProcessorInvoked.set(true);
                logger.info(inputPayload);
            })
            .build();

        final Flow flow = subject.createFlow(flowDefinition);
        Assert.assertNotNull(flow);

        final Service consumerService = flow.getConsumerAs(Service.class);
        consumerService.start();

        final Exchange exchange = new Exchange(new SimpleMessage("input"), new SimpleMessage("output"), new HeadersImpl());
        exchange.setInputPayload("ORIGINAL-PAYLOAD");
        flow.supply(exchange);

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(targetProcessorInvoked::get);

    }

}
