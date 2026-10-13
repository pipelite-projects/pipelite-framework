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
package io.pipelite.components.file;

import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.Headers;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The smallest {@link Exchange} the tests of this adapter need: an adapter only ever sees the interface,
 * and the framework's own implementation lives in core, which an adapter module cannot depend on.
 */
final class StubExchange implements Exchange, Headers {

    private final Map<String, Object> headers = new HashMap<>();
    private Object inputPayload;
    private Object outputPayload;

    void setInputPayload(Object inputPayload) {
        this.inputPayload = inputPayload;
    }

    Object getOutputPayload() {
        return outputPayload;
    }

    @Override
    public Headers getHeaders() {
        return this;
    }

    @Override
    public Optional<String> tryGetHeader(String headerName) {
        return Optional.ofNullable(headers.get(headerName)).map(String::valueOf);
    }

    @Override
    public String expectHeader(String headerName) {
        return tryGetHeader(headerName).orElseThrow(() -> new IllegalStateException("Header " + headerName + " is required"));
    }

    @Override
    public <T> Optional<T> tryGetHeaderAs(String headerName, Class<T> expectedType) {
        return Optional.ofNullable(headers.get(headerName)).map(expectedType::cast);
    }

    @Override
    public void putHeader(String headerName, Object headerValue) {
        headers.put(headerName, headerValue);
    }

    @Override
    public void removeHeader(String headerName) {
        headers.remove(headerName);
    }

    @Override
    public boolean hasHeader(String headerName) {
        return headers.containsKey(headerName);
    }

    @Override
    public Class<?> getInputPayloadType() {
        return inputPayload == null ? null : inputPayload.getClass();
    }

    @Override
    public Object getInputPayload() {
        return inputPayload;
    }

    @Override
    public <T> T getInputPayloadAs(Class<T> expectedType) {
        return expectedType.cast(inputPayload);
    }

    @Override
    public void setOutputPayload(Object payload) {
        this.outputPayload = payload;
    }
}
