package io.pipelite.core;

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import org.awaitility.Awaitility;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PipeliteHttpLogIntegrationTest {

    private PipeliteContext context;

    @Before
    public void setup() {
        context = new DefaultPipeliteContext();
    }

    @Test
    public void whenStart_thenProduceTimestampAndLog() {

        final AtomicInteger invocations = new AtomicInteger(0);

        final FlowDefinition flowDefinition = Pipelite.defineFlow("time-component-test")
            .fromSource("time://poll?period=100&timeUnit=MILLISECONDS")
            .process("verify-invocations", (ioContext, contribution) -> {
                invocations.incrementAndGet();
            })
            .toSink("slf4j://logger?level=INFO")
            .build();

        context.registerFlowDefinition(flowDefinition);
        context.start();

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> invocations.get() > 5);

        context.stop();

    }
}
