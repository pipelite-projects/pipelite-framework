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
package io.pipelite.common.support;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class ToStringBuilder {

    private final StringBuilder sb = new StringBuilder(32);

    private final Object object;

    private final boolean formatted;

    public static String reflection(Object object) {
        return reflection(object, true);
    }

    public static String reflection(Object object, boolean formatted) {
        ToStringBuilder builder = new ToStringBuilder(object, formatted);
        Field[] fields = object.getClass().getDeclaredFields();
        AccessibleObject.setAccessible(fields, true);
        for (Field field : fields) {
            String fieldName = field.getName();
            if (field.getName().indexOf('$') != -1) {
                continue;
            }
            if (Modifier.isTransient(field.getModifiers()) || (Modifier.isStatic(field.getModifiers()))) {
                continue;
            }
            try {
                Object fieldValue = field.get(object);
                builder.append(fieldName, fieldValue);
            }
            catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return builder.toString();
    }

    public ToStringBuilder(Object object) {
        this(object, true);
    }

    public ToStringBuilder(Object object, boolean formatted) {
        this.object = object;
        this.formatted = formatted;
    }

    public ToStringBuilder append(String fieldName, Object value) {
        Object tempValue = null;
        if (value == null) {
            tempValue = "<null>";
        }
        else if (value.getClass().isArray()) {
            tempValue = ToStringUtils.arrayToString((Object[]) value);
        }
        else {
            tempValue = value;
        }

        if (formatted) {
            sb.append("\t").append(fieldName).append(" = ").append(tempValue).append('\n');
        }
        else {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(fieldName).append('=').append(tempValue);
        }
        return this;
    }

    public String build() {
        return toString();
    }

    @Override
    public String toString() {
        String className = object.getClass().getSimpleName();
        return className + (formatted ? "[\n" : "[") + sb + ']';
    }

}
