package io.pipelite.components.http.support.convert;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pipelite.common.util.MimeType;

import java.io.IOException;

public class HttpMessageBodyJsonConverter extends AbstractHttpMessageBodyConverter {

    private final ObjectMapper objectMapper;

    public HttpMessageBodyJsonConverter() {
        this(null);
    }

    public HttpMessageBodyJsonConverter(ObjectMapper objectMapper){
        super(MimeType.valueOf("application/json"));
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    @Override
    public <T> Object convert(byte[] requestBody, MimeType contentType, Class<T> expectedType) {
        try {
            return objectMapper.readValue(requestBody, expectedType);
        } catch (IOException cause) {
            throw new HttpMessageBodyDeserializationException("Unable to deserialize http message body", cause);
        }
    }

}
