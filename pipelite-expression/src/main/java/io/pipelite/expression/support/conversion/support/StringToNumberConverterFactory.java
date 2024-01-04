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
package io.pipelite.expression.support.conversion.support;

import io.pipelite.expression.support.NumberUtils;
import io.pipelite.expression.support.StringUtils;
import io.pipelite.expression.support.conversion.impl.Converter;
import io.pipelite.expression.support.conversion.impl.ConverterFactory;

/**
 * @author Vincenzo Autiero
 *
 */
public class StringToNumberConverterFactory implements ConverterFactory<String, Number> {

    /*
     * (non-Javadoc)
     * 
     * @see com.enel.workbeat.des.engine.core.conversion.impl.ConverterFactory# getConverter(java.lang.Class)
     */
    @Override
    public <T extends Number> Converter<String, T> getConverter(Class<T> targetType) {
        return new StringToNumber<T>(targetType);
    }

    /**
     * @author ERICSSON
     *
     * @param <T>
     */
    public static class StringToNumber<T extends Number> implements Converter<String, T> {

        private final Class<T> targetType;

        public StringToNumber(Class<T> targetType) {
            this.targetType = targetType;
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.enel.workbeat.des.engine.core.conversion.impl.Converter#convert( java.lang.Object)
         */
        @Override
        public T convert(String source) {
            if (!StringUtils.hasText(source)) {
                return null;
            }
            return NumberUtils.parseNumber(source, targetType);
        }

    }

}
