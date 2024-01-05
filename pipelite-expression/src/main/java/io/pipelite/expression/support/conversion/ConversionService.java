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
package io.pipelite.expression.support.conversion;

import io.pipelite.expression.support.conversion.impl.Converter;
import io.pipelite.expression.support.conversion.impl.ConverterFactory;

/**
 * All implementations of this interface are thread-safe.
 * 
 * @author Vincenzo Autiero
 *
 */
public interface ConversionService {

    /**
     * @param sourceType
     * @param targetType
     * @param converter
     */
    public <S, T> void registerConverter(Class<?> sourceType, Class<?> targetType,
            Converter<? super S, ? extends T> converter);

    /**
     * @param sourceType
     * @param targetType
     * @param converterFactory
     */
    public void registerConverterFactory(Class<?> sourceType, Class<?> targetType,
            ConverterFactory<?, ?> converterFactory);

    /**
     * @param sourceType
     * @param targetType
     * @return
     */
    public boolean canConvert(Class<?> sourceType, Class<?> targetType);

    /**
     * @param source
     * @param targetType
     * @return
     */
    <T> T convert(Object source, Class<T> targetType);

}