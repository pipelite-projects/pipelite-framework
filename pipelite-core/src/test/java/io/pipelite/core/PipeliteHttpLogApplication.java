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
