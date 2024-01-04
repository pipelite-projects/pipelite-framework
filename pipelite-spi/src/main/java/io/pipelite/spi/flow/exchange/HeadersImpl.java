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
package io.pipelite.spi.flow.exchange;

import io.pipelite.dsl.Headers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class HeadersImpl extends HashMap<String, Object> implements Headers {

    public HeadersImpl(){
        super();
    }

    public HeadersImpl(Map<String, Object> headers){
        super(headers);
    }

    public HeadersImpl(HeadersImpl headers){
        super(headers);
    }

    public Optional<String> tryGetHeader(String headerName){
        return Optional.ofNullable(getHeader(headerName));
    }

    public String getHeader(String headerName){
        return getHeaderAs(headerName, String.class);
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
        return tryGetHeaderAs(headerName, String.class)
            .orElseThrow(() -> new IllegalArgumentException(String.format("Header with name '%s' is null", headerName)));
    }

    @Override
    public void putHeader(String headerName, Object headerValue) {
        put(headerName, headerValue);
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
