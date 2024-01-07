package io.pipelite.components.http.support.convert;

import io.pipelite.common.util.MimeType;

public interface HttpMessageBodyConverter {

    boolean supports(MimeType contentType);
    <T> Object convert(byte[] requestBody, MimeType contentType, Class<T> expectedType);

}
