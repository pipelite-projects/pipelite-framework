package io.pipelite.core.support.serialization;

public class ByteArrayWriteException extends RuntimeException {

    public ByteArrayWriteException(String message) {
        super(message);
    }

    public ByteArrayWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
