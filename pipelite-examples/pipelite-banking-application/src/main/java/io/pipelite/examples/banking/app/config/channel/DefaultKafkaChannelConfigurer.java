package io.pipelite.examples.banking.app.config.channel;

import io.pipelite.channels.kafka.config.KafkaChannelConfiguration;
import io.pipelite.channels.kafka.config.KafkaChannelConfigurer;
import io.pipelite.channels.kafka.config.PayloadMapping;
import io.pipelite.channels.kafka.support.convert.json.JsonDeserializerConfig;
import io.pipelite.channels.kafka.support.convert.json.JsonDeserializer;
import io.pipelite.channels.kafka.support.convert.json.JsonSerializer;
import io.pipelite.common.support.Preconditions;
import io.pipelite.examples.banking.infrastructure.message.TransactionMessage;
import io.pipelite.examples.banking.infrastructure.support.serialization.DefaultJacksonMapperConfigurator;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

public class DefaultKafkaChannelConfigurer implements KafkaChannelConfigurer {

    private final String bankId;

    public DefaultKafkaChannelConfigurer(String bankId) {
        this.bankId = Preconditions.notNull(bankId, "bankId is required and cannot be null");
    }

    @Override
    public void configure(KafkaChannelConfiguration configuration) {

        configuration.setBootstrapServers("localhost:9092");

        configuration.putConsumerGlobalConfig(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configuration.putConsumerGlobalConfig(JsonDeserializerConfig.KAFKA_JSON_OBJECTMAPPER_CONFIGURATOR_CLASS, DefaultJacksonMapperConfigurator.class);

        final String txChannelTopicName = String.format("banking.%s.tx.channel", bankId);

        configuration.putConsumerConfig(txChannelTopicName, ConsumerConfig.GROUP_ID_CONFIG, "banking");

        configuration.addPayloadMapping(PayloadMapping
            .define(txChannelTopicName)
            .withKeyDeserializer(StringDeserializer.class)
            .withValueDeserializer(JsonDeserializer.class)
            .withValueDeserializerDefaultType(TransactionMessage.class)
            .build());

        final String txRollbackTopicName = String.format("banking.%s.tx-rollback.channel", bankId);
        configuration.putConsumerConfig(txRollbackTopicName, ConsumerConfig.GROUP_ID_CONFIG, "banking");

        configuration.putProducerGlobalConfig(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configuration.putProducerGlobalConfig(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

    }

}
