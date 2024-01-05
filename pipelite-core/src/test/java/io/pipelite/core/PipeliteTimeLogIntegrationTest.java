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

import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.IdentityGenerator;
import io.pipelite.spi.flow.exchange.MessageFactory;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PipeliteTimeLogIntegrationTest {

    private PipeliteContext context;
    private ExchangeFactory exchangeFactory;

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
        IdentityGenerator identityGenerator = new DistributedIdentityGeneratorImpl();
        MessageFactory messageFactory = new DefaultMessageFactory(identityGenerator);
        exchangeFactory = new DefaultExchangeFactory(messageFactory);
    }

    @Test
    public void whenStart_thenProduceTimestampAndLog() {

        final AtomicInteger invocations = new AtomicInteger(0);

        final FlowDefinition flowDefinition = Pipelite.defineFlow("time-component-test")
            .fromSource("time://poll?period=10&timeUnit=MILLISECONDS")
            .process("verify-invocations", (ioContext, contribution) -> {
                invocations.incrementAndGet();
            })
            .toSink("slf4j://logger?level=INFO")
            .build();

        context.registerFlowDefinition(flowDefinition);

        context.start();
        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> invocations.get() > 50);

        context.stop();
        Assert.assertTrue(invocations.get() > 50);

    }
}
