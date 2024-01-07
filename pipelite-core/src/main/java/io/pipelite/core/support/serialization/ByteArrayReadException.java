package io.pipelite.core.support.serialization;

public class ByteArrayReadException extends RuntimeException {

    public ByteArrayReadException(String message) {
        super(message);
    }

    public ByteArrayReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
