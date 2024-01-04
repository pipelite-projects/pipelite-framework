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
package io.pipelite.expression.support.conversion.impl;

/**
 * @author
 *
 */
public class ConverterAdapter {

    private final Converter<Object, Object> converter;

    private final ConvertiblePair typeInfo;

    @SuppressWarnings("unchecked")
    public ConverterAdapter(Converter<?, ?> converter, Class<?> sourceType, Class<?> targetType) {
        this.converter = (Converter<Object, Object>) converter;
        typeInfo = new ConvertiblePair(sourceType, targetType);
    }

    /**
     * @param sourceType
     * @param targetType
     * @return
     */
    public boolean supports(Class<?> sourceType, Class<?> targetType) {
        return typeInfo.isAssignableFrom(sourceType, targetType);
    }

    /**
     * @param source
     * @param sourceType
     * @param targetType
     * @return
     */
    public Object convert(Object source) {
        if (source == null) {
            return null;
        }
        return converter.convert(source);
    }

}
