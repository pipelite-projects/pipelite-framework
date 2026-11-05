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
import io.pipelite.dsl.headers.HeaderPropagation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The one place where the headers that cross the border between a flow and the outside are chosen (issue #171): the
 * headers a source puts on the exchange, taken from what the protocol gave it, and the headers a sink hands to the other
 * system, taken from the exchange. A channel adapter hands over every header it has and never filters, so all of them
 * behave in the same way.
 * <p>
 * The names are compared ignoring the case, and a header that was chosen by name has on the other side the name the flow
 * wrote. The values cross as text: a number, a boolean or an enum is turned into its text, anything else is left behind,
 * since a header is a piece of text that is serialized and sent.
 */
public final class HeaderSelection {

    private static final Logger sysLogger = LoggerFactory.getLogger(HeaderSelection.class);

    private final HeaderPropagation propagation;

    public HeaderSelection(HeaderPropagation propagation) {
        this.propagation = Objects.requireNonNull(propagation, "propagation is required and cannot be null");
    }

    /** What is chosen by default: no header crosses. */
    public static HeaderSelection none() {
        return new HeaderSelection(HeaderPropagation.none());
    }

    public boolean isNone() {
        return propagation.isNone();
    }

    /** The headers that cross, from {@code headers}; empty when none was chosen or none matches. */
    public Map<String, String> select(Map<String, ?> headers) {
        if (headers == null || headers.isEmpty() || propagation.isNone()) {
            return Map.of();
        }
        final Map<String, String> selected = new LinkedHashMap<>();
        if (propagation.isAll()) {
            headers.forEach((name, value) -> put(selected, name, value));
            return selected;
        }
        for (String name : propagation.getNames()) {
            final Object value = lookup(headers, name);
            if (value != null) {
                put(selected, name, value);
            }
        }
        return selected;
    }

    /** The headers of an exchange that cross, for the sink. */
    @SuppressWarnings("unchecked")
    public Map<String, String> selectFrom(Headers headers) {
        return headers instanceof Map<?, ?> map ? select((Map<String, ?>) map) : Map.of();
    }

    private static Object lookup(Map<String, ?> headers, String name) {
        final Object exact = headers.get(name);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, ?> header : headers.entrySet()) {
            if (name.equalsIgnoreCase(header.getKey())) {
                return header.getValue();
            }
        }
        return null;
    }

    private static void put(Map<String, String> selected, String name, Object value) {
        if (name == null || value == null) {
            return;
        }
        final String text = asText(value);
        if (text == null) {
            if (sysLogger.isDebugEnabled()) {
                sysLogger.debug("Header '{}' is not propagated: its value is a {}, not text", name, value.getClass().getName());
            }
            return;
        }
        selected.put(name, text);
    }

    private static String asText(Object value) {
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character || value instanceof Enum<?>) {
            return String.valueOf(value);
        }
        return null;
    }
}
