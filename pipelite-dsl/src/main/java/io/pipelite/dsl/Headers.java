package io.pipelite.dsl;

import java.util.Optional;

public interface Headers {

    Optional<String> tryGetHeader(String headerName);
    String expectHeader(String headerName);
    <T> Optional<T> tryGetHeaderAs(String headerName, Class<T> expectedType);
    void putHeader(String headerName, Object headerValue);
    void removeHeader(String headerName);
    boolean hasHeader(String headerName);

}
