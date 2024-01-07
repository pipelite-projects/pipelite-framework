package io.pipelite.channels.kafka.config;

import io.pipelite.common.support.Builder;
import io.pipelite.common.support.Preconditions;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Optional;

public class PayloadMapping {

    private final String topicName;

    private Class<? extends Deserializer<?>> keyDeserializer;
    private Class<? extends Deserializer<?>> valueDeserializer;
    private Class<?> valueDeserializerDefaultType;

    private Class<? extends Serializer<?>> keySerializer;
    private Class<? extends Serializer<?>> valueSerializer;

    public static PayloadMappingBuilder define(String topicName){
        return new PayloadMappingBuilder(topicName);
    }

    public PayloadMapping(String topicName) {
        this.topicName = Preconditions.hasText(topicName, "Illegal topicName provided");
    }

    public String getTopicName() {
        return topicName;
    }

    public Optional<Class<? extends Deserializer<?>>> tryGetKeyDeserializer() {
        return Optional.ofNullable(keyDeserializer);
    }

    public Optional<Class<? extends Deserializer<?>>> tryGetValueDeserializer() {
        return Optional.ofNullable(valueDeserializer);
    }

    public Optional<Class<?>> tryGetValueDeserializerDefaultType() {
        return Optional.ofNullable(valueDeserializerDefaultType);
    }

    public Class<? extends Serializer<?>> getKeySerializer() {
        return keySerializer;
    }

    public Class<? extends Serializer<?>> getValueSerializer() {
        return valueSerializer;
    }

    public static final class PayloadMappingBuilder {

        private final Builder<PayloadMapping> builder;

        private PayloadMappingBuilder(String topicName) {

            Preconditions.hasText(topicName, "Invalid topicName provided");

            builder = Builder.forType(PayloadMapping.class);
            builder.constructWith(topicName);

        }

        public PayloadMappingBuilder withKeyDeserializer(Class<? extends Deserializer<?>> keyDeserializer){
            builder.with(i -> i.keyDeserializer = keyDeserializer);
            return this;
        }

        public PayloadMappingBuilder withValueDeserializer(Class<? extends Deserializer<?>> valueDeserializer){
            builder.with(i -> i.valueDeserializer = valueDeserializer);
            return this;
        }

        public PayloadMappingBuilder withKeySerializer(Class<? extends Serializer<?>> keySerializer){
            builder.with(i -> i.keySerializer = keySerializer);
            return this;
        }

        public PayloadMappingBuilder withValueSerializer(Class<? extends Serializer<?>> valueSerializer){
            builder.with(i -> i.valueSerializer = valueSerializer);
            return this;
        }

        public PayloadMappingBuilder withValueDeserializerDefaultType(Class<?> valueDeserializerDefaultType){
            builder.with(i -> i.valueDeserializerDefaultType = valueDeserializerDefaultType);
            return this;
        }

        public PayloadMapping build(){
            return builder.build();
        }
    }
}
