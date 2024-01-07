package io.pipelite.channels.kafka.support.convert.json;

public class JsonDeserializerConfig {

    public static final String KAFKA_JSON_OBJECTMAPPER_CONFIGURATOR_CLASS = "kafka.json.objectmapper.coinfigurator.class";

    /**
     * Kafka config property for the default key type if no header.
     */
    public static final String KEY_DEFAULT_TYPE = "kafka.json.key.default.type";

    /**
     * Kafka config property for the default value type if no header.
     */
    public static final String VALUE_DEFAULT_TYPE = "kafka.json.value.default.type";


}
