package io.pipelite.core;

import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;

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
            .toRoute(routeConfigurator -> routeConfigurator.dynamicRouting()
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
