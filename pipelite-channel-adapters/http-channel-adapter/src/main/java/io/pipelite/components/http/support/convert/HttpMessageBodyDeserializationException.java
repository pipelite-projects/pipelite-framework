package io.pipelite.components.http.support.convert;

public class HttpMessageBodyDeserializationException extends RuntimeException {

    public HttpMessageBodyDeserializationException(String message) {
        super(message);
    }

    public HttpMessageBodyDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }

}
