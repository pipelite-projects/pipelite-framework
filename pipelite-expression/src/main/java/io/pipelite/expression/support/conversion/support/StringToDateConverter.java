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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.pipelite.common.support.Preconditions;
import io.pipelite.expression.support.conversion.exception.CannotConvertValueException;
import io.pipelite.expression.support.conversion.impl.Converter;

/**
 * @author Vincenzo Autiero
 *
 */
public class StringToDateConverter implements Converter<String, Date> {

    private final DateFormat dateFormat;

    /**
     * @param pattern
     */
    public StringToDateConverter(String pattern) {
        Preconditions.notNull(pattern, "Pattern is required");
        dateFormat = new SimpleDateFormat(pattern);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.enel.workbeat.des.engine.core.conversion.impl.Converter#convert(java. lang.Object)
     */
    @Override
    public Date convert(String source) {
        try {
            return dateFormat.parse(source);
        }
        catch (ParseException cause) {
            throw new CannotConvertValueException(String.format("Unrecognized date format %s", source), cause);
        }
    }

}
