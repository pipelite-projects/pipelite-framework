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
package io.pipelite.expression.core.el.bean;

public class BeanExpressionToken {

    private final String value;

    private TokenType tokenType;

    public BeanExpressionToken(String value) {
        this.value = value;
    }

    public BeanExpressionToken(String value, TokenType tokenType) {
        this.value = value;
        this.tokenType = tokenType;
    }

    public String getValue() {
        return value;
    }

    public TokenType getTokenType() {
        return tokenType;
    }

    public void setTokenType(TokenType tokenType) {
        this.tokenType = tokenType;
    }

    public String asText() {
        return tokenType.asText(value);
    }

    @Override
    public String toString() {
        return asText();
    }

    public static enum TokenType {

        VARIABLE("%s"), ARRAY_ACCESS("[%s]"), LIST_ACCESS("(%s)"), MAP_ACCESS("['%s']"), BEAN_PROPERTY_ACCESS("%s");

        private final String textPattern;

        private TokenType(String textPattern) {
            this.textPattern = textPattern;
        }

        public String asText(String value) {
            return String.format(textPattern, value);
        }

    }

}
