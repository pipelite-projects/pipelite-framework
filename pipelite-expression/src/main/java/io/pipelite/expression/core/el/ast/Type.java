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
package io.pipelite.expression.core.el.ast;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;

public enum Type {

    ENUM(String.class), TEXT(String.class), BOOLEAN(Boolean.class), NUMERIC(BigDecimal.class), ANY(Object.class);

    Type(Class<?> type) {
        this.type = type;
    }

    private final Class<?> type;

    public Class<?> getJavaType() {
        return this.type;
    }

    public boolean supports(Class<?> javaType) {
        return type.isAssignableFrom(javaType);
    }

    public static Type resolveAssignable(Object value){
        return resolveAssignable(value.getClass());
    }

    public static Type resolveAssignable(Class<?> javaType){
        return Arrays.stream(values())
            //.sorted(Comparator.comparing(Type::ordinal).reversed())
            .filter(value -> value.supports(javaType))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(String.format("Unable to resolve Type from '%s'", javaType)));
    }

}
