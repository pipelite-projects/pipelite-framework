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
package io.pipelite.spi.endpoint;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A read-only snapshot of an {@link Endpoint}'s {@code EndpointURL} query-string parameters.
 * Every mutating {@link Map} method throws {@link UnsupportedOperationException} — {@link
 * Endpoint#getProperties()} previously returned this as a plain, freely-mutable {@code HashMap},
 * which invited a caller to "configure" an endpoint via {@code endpoint.getProperties().put(...)}
 * with no effect whatsoever (each call already returns a fresh copy, per {@code
 * EndpointURL#getProperties()} — mutating it was always silently pointless, never dangerous to
 * live state, but the mutable type signature actively misled callers into thinking otherwise).
 * Failing fast here surfaces that mistake immediately instead of silently doing nothing.
 */
public class EndpointProperties extends HashMap<String, String> {

    public EndpointProperties(){
    }

    public EndpointProperties(Map<String, String> properties){
        super(properties);
    }

    @Override
    public String put(String key, String value) {
        throw new UnsupportedOperationException("EndpointProperties is read-only");
    }

    @Override
    public void putAll(Map<? extends String, ? extends String> other) {
        throw new UnsupportedOperationException("EndpointProperties is read-only");
    }

    @Override
    public String putIfAbsent(String key, String value) {
        throw new UnsupportedOperationException("EndpointProperties is read-only");
    }

    @Override
    public String remove(Object key) {
        throw new UnsupportedOperationException("EndpointProperties is read-only");
    }

    @Override
    public boolean remove(Object key, Object value) {
        throw new UnsupportedOperationException("EndpointProperties is read-only");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("EndpointProperties is read-only");
    }

    @Override
    public String replace(String key, String value) {
        throw new UnsupportedOperationException("EndpointProperties is read-only");
    }

    @Override
    public boolean replace(String key, String oldValue, String newValue) {
        throw new UnsupportedOperationException("EndpointProperties is read-only");
    }

    @Override
    public void replaceAll(BiFunction<? super String, ? super String, ? extends String> function) {
        throw new UnsupportedOperationException("EndpointProperties is read-only");
    }

    @Override
    public String merge(String key, String value, BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
        throw new UnsupportedOperationException("EndpointProperties is read-only");
    }

    @Override
    public String computeIfAbsent(String key, Function<? super String, ? extends String> mappingFunction) {
        throw new UnsupportedOperationException("EndpointProperties is read-only");
    }

    @Override
    public String computeIfPresent(String key, BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
        throw new UnsupportedOperationException("EndpointProperties is read-only");
    }

    @Override
    public String compute(String key, BiFunction<? super String, ? super String, ? extends String> remappingFunction) {
        throw new UnsupportedOperationException("EndpointProperties is read-only");
    }

    public Integer getAsInteger(String key){
        final String valueAsText = get(key);
        return valueAsText != null ? Integer.parseInt(valueAsText) : null;
    }

    public Integer getAsIntegerOrDefault(String key, Integer defaultValue){
        final String valueAsText = get(key);
        return valueAsText != null ? Integer.parseInt(valueAsText) : defaultValue;
    }

    public Long getAsLong(String key){
        final String valueAsText = get(key);
        return valueAsText != null ? Long.parseLong(valueAsText) : null;
    }

    public Long getAsLongOrDefault(String key, Long defaultValue){
        final String valueAsText = get(key);
        return valueAsText != null ? Long.parseLong(valueAsText) : defaultValue;
    }

    public Boolean getAsBooleanOrDefault(String key, boolean defaultValue){
        final String valueAsText = get(key);
        return valueAsText != null ? Boolean.parseBoolean(valueAsText) : defaultValue;
    }

}
