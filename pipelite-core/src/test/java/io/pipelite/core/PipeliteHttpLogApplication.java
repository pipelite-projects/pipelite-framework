package io.pipelite.core;

import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.flow.FlowFactory;
import io.pipelite.dsl.definition.FlowDefinition;

public class PipeliteHttpLogApplication {

    private final PipeliteContext context;

    public PipeliteHttpLogApplication() {
        this.context = new DefaultPipeliteContext();
        //context.registerComponent("http", new HttpComponent());
        //context.registerComponent("slf4j", new Slf4jComponent());
    }

    public void run(){

        final FlowFactory flowFactory = new FlowFactory(context);

        final FlowDefinition flowDefinition01 = Pipelite.defineFlow("time-component-test")
            .fromSource("http://channel-01")
            .transformPayload("transform-payload", inputPayload -> String.format("Payload from http://channel-01: '%s'", inputPayload))
            .toSink("slf4j://logger?level=DEBUG")
            .build();

        final FlowDefinition flowDefinition02 = Pipelite.defineFlow("time-component-test")
            .fromSource("http://channel-02")
            .transformPayload("transform-payload", inputPayload -> String.format("Payload from http://channel-02: '%s'", inputPayload))
            .toSink("slf4j://logger?level=WARN")
            .build();

        //final Flow flow01 = flowFactory.createFlow(flowDefinition01);
        //final Flow flow02 = flowFactory.createFlow(flowDefinition02);

        context.registerFlowDefinition(flowDefinition01);
        context.registerFlowDefinition(flowDefinition02);
        context.start();
    }

    public static void main(String[] args){

        PipeliteHttpLogApplication app = new PipeliteHttpLogApplication();
        app.run();

    }
}
