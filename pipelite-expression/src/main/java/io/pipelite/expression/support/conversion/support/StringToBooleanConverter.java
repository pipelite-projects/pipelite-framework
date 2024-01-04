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

import java.util.HashSet;
import java.util.Set;

import io.pipelite.expression.support.StringUtils;
import io.pipelite.expression.support.conversion.exception.CannotConvertValueException;
import io.pipelite.expression.support.conversion.impl.Converter;

/**
 * @author Vincenzo Autiero
 *
 */
public class StringToBooleanConverter implements Converter<String, Boolean> {

    private static final Set<String> trueValues = new HashSet<String>(4);

    private static final Set<String> falseValues = new HashSet<String>(4);

    static {
        trueValues.add("true");
        trueValues.add("on");
        trueValues.add("1");
        trueValues.add("yes");
        falseValues.add("false");
        falseValues.add("off");
        falseValues.add("0");
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.enel.workbeat.des.engine.core.conversion.impl.Converter#convert(java. lang.Object)
     */
    @Override
    public Boolean convert(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }
        String sourceLower = source.toLowerCase();
        if (trueValues.contains(sourceLower)) {
            return Boolean.TRUE;
        }
        else if (falseValues.contains(sourceLower)) {
            return Boolean.FALSE;
        }
        else {
            throw new CannotConvertValueException("Invalid boolean value '" + sourceLower + "'");
        }
    }

}
