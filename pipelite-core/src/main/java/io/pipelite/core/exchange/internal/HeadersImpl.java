/*
 * Copyright (C) 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pipelite.core.exchange.internal;

import io.pipelite.dsl.Headers;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The headers of an exchange (issue #166). The value of a header is a scalar: a {@link String}, a {@link Boolean} or a
 * number ({@link Byte}, {@link Short}, {@link Integer}, {@link Long}, {@link Float}, {@link Double}, {@link
 * java.math.BigInteger}, {@link java.math.BigDecimal}), kept with its exact type. A header cannot hold {@code null}:
 * putting one removes the header. Anything else is refused where it is put, with the name of the header and the type,
 * because headers are written to disk (the inbox, the retry dumps, the dead letter queue, the barriers) and an object
 * of the application there ties what is on disk to the version of its class, and fails far from where it was put if it
 * cannot be serialized.
 */
public class HeadersImpl extends HashMap<String, Object> implements Headers {

    public HeadersImpl(){
        super();
    }

    public HeadersImpl(Map<String, Object> headers){
        super();
        putAll(headers);
    }

    public HeadersImpl(HeadersImpl headers){
        super(headers);
    }

    /** Whether {@code value} is of a type a header can hold. */
    public static boolean isScalar(Object value) {
        return value instanceof String || value instanceof Boolean
            || value instanceof Integer || value instanceof Long || value instanceof Double
            || value instanceof Short || value instanceof Byte || value instanceof Float
            || value instanceof BigInteger || value instanceof BigDecimal;
    }

    /**
     * The text of the header, whatever its type: {@code String.valueOf} of a number or a boolean. Absent when the header
     * is. {@link #tryGetHeaderAs} is the one that checks the type.
     */
    public Optional<String> tryGetHeader(String headerName){
        return Optional.ofNullable(getHeader(headerName));
    }

    public String getHeader(String headerName){
        final Object value = get(headerName);
        return value == null ? null : String.valueOf(value);
    }

    public <T> Optional<T> tryGetHeaderAs(String headerName, Class<T> expectedType){
        return Optional.ofNullable(getHeaderAs(headerName, expectedType));
    }

    public <T> T getHeaderAs(String headerName, Class<T> expectedType){
        final Object value = get(headerName);
        if(value == null){
            return null;
        } else if(expectedType.isAssignableFrom(value.getClass())){
            return expectedType.cast(value);
        }
        throw new ClassCastException(String.format("Cannot cast from %s to expectedType %s", value.getClass(), expectedType));
    }

    @Override
    public String expectHeader(String headerName) {
        return tryGetHeader(headerName)
            .orElseThrow(() -> new IllegalArgumentException(String.format("Header with name '%s' is null", headerName)));
    }

    /**
     * @throws IllegalArgumentException if the value is not a scalar, naming the header and the type; a {@code null}
     * value removes the header
     */
    @Override
    public void putHeader(String headerName, Object headerValue) {
        put(headerName, headerValue);
    }

    /** Same rule as {@link #putHeader}, for whoever reaches the headers as a {@code Map}. */
    @Override
    public Object put(String headerName, Object headerValue) {
        if (headerValue == null) {
            return remove(headerName);
        }
        if (!isScalar(headerValue)) {
            throw new IllegalArgumentException(String.format(
                "Header '%s' cannot hold a value of type %s: a header is a String, a Boolean or a number (Byte, Short, "
                    + "Integer, Long, Float, Double, BigInteger, BigDecimal). Put the object in the payload, or in a "
                    + "property if it is not to leave the flow.", headerName, headerValue.getClass().getName()));
        }
        return super.put(headerName, headerValue);
    }

    @Override
    public void putAll(Map<? extends String, ?> headers) {
        headers.forEach(this::put);
    }

    @Override
    public void removeHeader(String headerName) {
        remove(headerName);
    }

    @Override
    public boolean hasHeader(String headerName) {
        return containsKey(headerName);
    }
}
