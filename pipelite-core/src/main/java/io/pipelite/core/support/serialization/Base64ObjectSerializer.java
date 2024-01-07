package io.pipelite.core.support.serialization;

public class Base64ObjectSerializer implements ObjectSerializer {

    private final ObjectToByteArrayConverter byteArrayConverter;

    public Base64ObjectSerializer() {
        this.byteArrayConverter = new ObjectToByteArrayConverter();
    }

    @Override
    public String getEncoding() {
        return "base64";
    }

    @Override
    public String serializeObject(Object input) {
        final byte[] content = byteArrayConverter.convert(input);
        return BaseEncoding.base64().encode(content);
    }
}
