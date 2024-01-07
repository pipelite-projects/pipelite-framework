package io.pipelite.channels.kafka.support.convert.json;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.channel.convert.json.ObjectMapperConfigurator;
import org.apache.kafka.common.serialization.Deserializer;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import static io.pipelite.channels.kafka.support.convert.json.JsonDeserializerConfig.KAFKA_JSON_OBJECTMAPPER_CONFIGURATOR_CLASS;
import static io.pipelite.channels.kafka.support.convert.json.JsonDeserializerConfig.KEY_DEFAULT_TYPE;
import static io.pipelite.channels.kafka.support.convert.json.JsonDeserializerConfig.VALUE_DEFAULT_TYPE;

public class JsonDeserializer implements Deserializer<Object> {

    private final ObjectMapper objectMapper;

    private ObjectReader objectReader;

    private JavaType targetType;

    private boolean useTypeHeaders;

    public JsonDeserializer(){
        this(null);
    }

    public JsonDeserializer(ObjectMapper objectMapper){
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public void configure(Map<String, ?> configs, boolean isKey) {

        final Object objectMapperConfiguratorClassObject = configs.get(KAFKA_JSON_OBJECTMAPPER_CONFIGURATOR_CLASS);

        if(objectMapperConfiguratorClassObject != null){

            Preconditions.state(objectMapperConfiguratorClassObject instanceof Class<?>, String.format("'%s' config property must be an instance of Class", KAFKA_JSON_OBJECTMAPPER_CONFIGURATOR_CLASS));
            final Class<?> objectMapperConfiguratorClass = (Class<?>) objectMapperConfiguratorClassObject;

            Preconditions.state(ObjectMapperConfigurator.class.isAssignableFrom(objectMapperConfiguratorClass), String.format("%s must extends %s", objectMapperConfiguratorClass, ObjectMapperConfigurator.class));

            try {
                final Constructor<?> ctr = objectMapperConfiguratorClass.getConstructor();
                final Object objectMapperConfiguratorInstance = ctr.newInstance();
                final ObjectMapperConfigurator objectMapperConfigurator = (ObjectMapperConfigurator) objectMapperConfiguratorInstance;

                objectMapperConfigurator.configure(objectMapper);

            } catch (NoSuchMethodException | InvocationTargetException | InstantiationException |
                     IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        setupTarget(configs, isKey);
    }

    @Override
    public Object deserialize(String topic, byte[] data) {
        try {
            return objectReader.readValue(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupTarget(Map<String, ?> configs, boolean isKey) {
        try {
            JavaType javaType = null;
            if (isKey && configs.containsKey(KEY_DEFAULT_TYPE)) {
                javaType = setupTargetType(configs, KEY_DEFAULT_TYPE);
            }
            else if (!isKey && configs.containsKey(VALUE_DEFAULT_TYPE)) {
                javaType = setupTargetType(configs, VALUE_DEFAULT_TYPE);
            }

            if (javaType != null) {
                initialize(javaType, false /*TypePrecedence.TYPE_ID.equals(this.typeMapper.getTypePrecedence())*/);
            }
        }
        catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException(e);
        }
    }

    private JavaType setupTargetType(Map<String, ?> configs, String key) throws ClassNotFoundException, LinkageError {
        final Object configKey = configs.get(key);
        if (configKey instanceof Class) {
            return TypeFactory.defaultInstance().constructType((Class<?>) configKey);
        }
        else if (configKey instanceof String) {
            return TypeFactory.defaultInstance()
                .constructType(Class.forName((String) configKey));
        }
        else {
            throw new IllegalStateException(key + " must be Class or String");
        }
    }

    private void initialize(JavaType type, boolean useHeadersIfPresent) {
        this.targetType = type;
        this.useTypeHeaders = useHeadersIfPresent;
        Preconditions.state(this.targetType != null || useHeadersIfPresent,
            "'targetType' cannot be null if 'useHeadersIfPresent' is false");

        if (this.targetType != null) {
            this.objectReader = objectMapper.readerFor(this.targetType);
        }

        //addTargetPackageToTrusted();
        //this.typeMapper.setTypePrecedence(useHeadersIfPresent ? TypePrecedence.TYPE_ID : TypePrecedence.INFERRED);
    }
}
