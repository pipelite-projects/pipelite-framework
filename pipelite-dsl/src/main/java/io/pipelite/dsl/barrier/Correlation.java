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
package io.pipelite.dsl.barrier;

import java.util.Objects;

/**
 * How a key is read from an exchange: a header, or an expression. A value, not code: it is what the user
 * declared, read by the framework on the exchange that waits and on the exchange of the awaited flow. Obtained
 * from {@link CorrelationOperations}.
 */
public final class Correlation {

    public enum Kind {
        HEADER, EXPRESSION
    }

    private final Kind kind;
    private final String text;

    private Correlation(Kind kind, String text) {
        this.kind = kind;
        this.text = text;
    }

    /** For {@link CorrelationOperations#headerName}; not meant to be called from user code. */
    public static Correlation ofHeader(String headerName) {
        return new Correlation(Kind.HEADER, requireText(headerName, "headerName"));
    }

    /** For {@link CorrelationOperations#expression}; not meant to be called from user code. */
    public static Correlation ofExpression(String expression) {
        return new Correlation(Kind.EXPRESSION, requireText(expression, "expression"));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required and cannot be null or empty");
        }
        return value;
    }

    public Kind getKind() {
        return kind;
    }

    /** The header name or the expression, according to {@link #getKind()}. */
    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Correlation that && kind == that.kind && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, text);
    }

    @Override
    public String toString() {
        return kind == Kind.HEADER ? "header '" + text + "'" : "expression '" + text + "'";
    }
}
