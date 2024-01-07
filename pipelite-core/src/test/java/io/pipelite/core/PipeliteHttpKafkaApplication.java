package io.pipelite.core;

import io.pipelite.channels.kafka.config.KafkaChannelConfigurer;
import io.pipelite.channels.kafka.support.convert.json.JsonSerializer;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import org.apache.kafka.clients.producer.ProducerConfig;

public class PipeliteHttpKafkaApplication {

    private final PipeliteContext context;

    public PipeliteHttpKafkaApplication() {
        this.context = new DefaultPipeliteContext();
        this.context.addChannelConfigurer((KafkaChannelConfigurer) configuration -> {
            configuration.putProducerConfig("pipelite-inbound-topic", ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        });
    }

    public void run() {

        final FlowDefinition kafkaFlow = Pipelite.defineFlow("kafka-to-kafka")
            .fromSource("kafka://pipelite-inbound-topic?group.id=pipelite-consumers-01")
            .wireTap("kafka-to-kafka", "slf4j://kafka-to-kafka")
            .transformPayload("transform-kafka-message", inputPayload -> {
                final String message = String.format("This is the message -> '%s'", inputPayload.getPayloadAs(String.class));
                OutputMessage outputMessage = new OutputMessage(message);
                outputMessage.setOrigin("http-channel");
                return outputMessage;
            })
            .toSink("kafka://pipelite-outbound-topic")
            .build();

        final FlowDefinition kafkaLogFlow = Pipelite.defineFlow("kafka-to-log")
            .fromSource("kafka://pipelite-inbound-topic?group.id=pipelite-consumers-02")
            .toSink("slf4j://kafka-to-log")
            .build();

        context.registerFlowDefinition(kafkaFlow);
        context.registerFlowDefinition(kafkaLogFlow);
        context.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook thread id is " + Thread.currentThread());
            context.stop();
        }));

    }

    public static void main(String[] args) {

        PipeliteHttpKafkaApplication app = new PipeliteHttpKafkaApplication();
        app.run();

    }

    public static class OutputMessage {

        private final String message;

        private String origin;

        public OutputMessage(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }

        public String getOrigin() {
            return origin;
        }

        public void setOrigin(String origin) {
            this.origin = origin;
        }
    }

}
