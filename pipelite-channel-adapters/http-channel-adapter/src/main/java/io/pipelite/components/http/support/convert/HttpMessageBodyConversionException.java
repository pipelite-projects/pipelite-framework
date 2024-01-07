package io.pipelite.components.http.support.convert;

public class HttpMessageBodyConversionException extends RuntimeException {

    public HttpMessageBodyConversionException(String message) {
        super(message);
    }

    public HttpMessageBodyConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
