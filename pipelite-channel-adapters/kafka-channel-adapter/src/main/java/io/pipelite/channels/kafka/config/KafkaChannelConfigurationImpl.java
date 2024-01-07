package io.pipelite.channels.kafka.config;

import io.pipelite.common.support.Preconditions;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class KafkaChannelConfigurationImpl implements KafkaChannelConfiguration {

    private static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";

    private final Map<String, PayloadMapping> topicMappings = new ConcurrentHashMap<>();

    private String bootstrapServers;

    private Map<String,Object> consumerGlobalConfig;
    private Map<String,Object> producerGlobalConfig;

    private final Map<String, Map<String,Object>> consumerConfigMap = new LinkedHashMap<>();
    private final Map<String, Map<String,Object>> producerConfigMap = new LinkedHashMap<>();

    @Override
    public void setBootstrapServers(String bootstrapServers) {
        Preconditions.hasText(bootstrapServers, "Illegal configuration value provided, bootstrapServers");
        this.bootstrapServers = bootstrapServers;
    }

    public String getBootstrapServers() {
        return bootstrapServers != null ? bootstrapServers : DEFAULT_BOOTSTRAP_SERVERS;
    }

    @Override
    public Map<String, Object> getProducerConfig(String topicName) {
        return mergeConfigs(getProducerGlobalConfig(),
            producerConfigMap.getOrDefault(topicName, new LinkedHashMap<>()));
    }

    @Override
    public void putProducerConfig(String topicName, String key, Object value) {
        producerConfigMap.computeIfAbsent(topicName, k -> new LinkedHashMap<>())
            .put(key,value);
    }

    @Override
    public void putProducerGlobalConfig(String key, Object value) {
        if(producerGlobalConfig == null){
            producerGlobalConfig = createDefaultProducerConfig();
        }
        producerGlobalConfig.put(key, value);
    }

    @Override
    public Map<String,Object> getProducerGlobalConfig() {
        if(producerGlobalConfig == null){
            producerGlobalConfig = createDefaultProducerConfig();
        }
        return new HashMap<>(producerGlobalConfig);
    }

    @Override
    public Map<String, Object> getConsumerConfig(String topicName) {
        return mergeConfigs(getConsumerGlobalConfig(),
            consumerConfigMap.getOrDefault(topicName, new LinkedHashMap<>()));
    }

    @Override
    public void putConsumerConfig(String topicName, String key, Object value) {
        consumerConfigMap.computeIfAbsent(topicName, k -> new LinkedHashMap<>())
            .put(key,value);
    }

    @Override
    public void putConsumerGlobalConfig(String key, Object value) {
        if(consumerGlobalConfig == null){
            consumerGlobalConfig = createDefaultConsumerConfig();
        }
        consumerGlobalConfig.put(key, value);
    }

    @Override
    public void addPayloadMapping(PayloadMapping payloadMapping) {
        topicMappings.putIfAbsent(payloadMapping.getTopicName(), payloadMapping);
    }

    @Override
    public Optional<PayloadMapping> resolvePayloadMapping(String topicName) {
        return Optional.ofNullable(topicMappings.get(topicName));
    }

    private Map<String, Object> getConsumerGlobalConfig() {
        if(consumerGlobalConfig == null){
            consumerGlobalConfig = createDefaultConsumerConfig();
        }
        return new HashMap<>(consumerGlobalConfig);
    }

    private static Map<String,Object> createDefaultConsumerConfig(){
        final Map<String,Object> consumerConfig = new HashMap<>();
        consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return consumerConfig;
    }

    private static Map<String,Object> createDefaultProducerConfig(){
        final Map<String,Object> producerConfig = new HashMap<>();
        producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return producerConfig;
    }

    private static Map<String, Object> mergeConfigs(Map<String,Object> source, Map<String,Object> target){
        target.putAll(source);
        return target;
    }
}
