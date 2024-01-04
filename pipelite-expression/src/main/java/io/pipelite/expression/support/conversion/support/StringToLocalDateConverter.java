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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import io.pipelite.common.support.Preconditions;
import io.pipelite.expression.support.conversion.exception.CannotConvertValueException;
import io.pipelite.expression.support.conversion.impl.Converter;

/**
 * @author Vincenzo Autiero
 *
 */
public class StringToLocalDateConverter implements Converter<String, LocalDate> {

    private final DateTimeFormatter formatter;

    /**
     * @param pattern
     */
    public StringToLocalDateConverter(String pattern) {
        Preconditions.notNull(pattern, "Pattern is required");
        formatter = DateTimeFormatter.ofPattern(pattern);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.enel.workbeat.des.engine.core.conversion.impl.Converter#convert(java. lang.Object)
     */
    @Override
    public LocalDate convert(String source) {
        try {
            return LocalDate.parse(source, formatter);
        }
        catch (DateTimeParseException cause) {
            throw new CannotConvertValueException(String.format("Unrecognized date format %s", source), cause);
        }
    }

}
