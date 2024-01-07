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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

import io.pipelite.expression.support.conversion.support.*;

public class DefaultConversionConfiguration implements ConversionConfiguration {

    private static final String DATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Override
    public void configureConverters(ConverterRegistry registry) {

        registry.registerConverter(Number.class, String.class, new NumberToStringConverter());
        registry.registerConverter(String.class, Boolean.class, new StringToBooleanConverter());
        registry.registerConverter(Boolean.class, String.class, new BooleanToStringConverter());
        registry.registerConverter(String.class, Date.class, new StringToDateConverter(DATE_PATTERN));
        registry.registerConverter(String.class, LocalDate.class, new StringToLocalDateConverter(DATE_PATTERN));
        registry.registerConverter(String.class, LocalDateTime.class, new StringToLocalDateTimeConverter(DATE_PATTERN));
        registry.registerConverter(Enum.class, String.class, new EnumToStringConverter());

        registry.registerConverterFactory(Number.class, Number.class, new NumberToNumberConverterFactory());
        registry.registerConverterFactory(String.class, Number.class, new StringToNumberConverterFactory());
        registry.registerConverterFactory(String.class, Enum.class, new StringToEnumConverterFactory());

    }

}
