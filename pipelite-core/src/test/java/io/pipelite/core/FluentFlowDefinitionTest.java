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
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.definition.builder.error.ErrorChannelOperations;

public class FluentFlowDefinitionTest {

    public void whenDefineFlow_thenShouldBeStraightforward() {

        final PipeliteContext context = new DefaultPipeliteContext();
        final FlowDefinition flowDefinition = Pipelite.defineFlow("simple-flow")
            .fromSource("rabbit://topicName")
            .transformPayload("transform-rabbit-message",
                inputPayload -> String.format("This is transformed payload, original is '%s'", inputPayload))
            .process("process-message", (ioContext, processContribution) -> {
                final String payload = ioContext.getInputPayloadAs(String.class);
                ioContext.setOutputPayload(String.format("Processed payload is '%s'", payload));
            })
            .filter("filter-payload", "Payload.startsWith('UnitedStates-')")
            .toRoute(routeConfigurator -> routeConfigurator.dynamic()
                .when("#inputPayload.destination == 'CAESAR-PALACE'")
                .then("rabbit://route66?exit=LasVegas")
                .when("#inputPayload.destination == 'SANTA-MONICA'")
                .then("rabbit://route66?exit=LosAngeles")
                .otherwise("direct://home")
                .end())
            .build();

        final FlowDefinition recipientListFlow = Pipelite.defineFlow("recipient-list-flow")
            .fromSource("rabbit://topicName")
            .toSink("end")
            .withRetryChannel()
            //.withErrorChannel()
            //.toRecipientList(RecipientListImpl.of("http://example.com:8080/acquire-message", "direct://cc-flow"))
            .build();

        context.registerFlowDefinition(flowDefinition);
        context.registerFlowDefinition(recipientListFlow);

        context.start();
    }

}
