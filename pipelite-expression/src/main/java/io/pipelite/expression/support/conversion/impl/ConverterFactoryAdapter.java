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
public class ConverterFactoryAdapter {

    private final ConverterFactory<Object, Object> converterFactory;

    private final ConvertiblePair typeInfo;

    /**
     * @param converterFactory
     * @param sourceType
     * @param targetType
     */
    @SuppressWarnings("unchecked")
    public ConverterFactoryAdapter(ConverterFactory<?, ?> converterFactory, Class<?> sourceType, Class<?> targetType) {
        this.converterFactory = (ConverterFactory<Object, Object>) converterFactory;
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
     * @return
     */
    public ConverterFactory<Object, Object> getConverterFactory() {
        return converterFactory;
    }

}
