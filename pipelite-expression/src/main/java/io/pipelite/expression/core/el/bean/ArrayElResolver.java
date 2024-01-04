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
package io.pipelite.expression.core.el.bean;

import java.lang.reflect.Array;

public class ArrayElResolver implements ElResolver {

    public Object resolveValue(ElContext context, Object target, Object property) {
        if (target != null && target.getClass().isArray()) {
            int index = toInteger(property);
            if (index >= 0 && index < Array.getLength(target)) {
                context.setPropertyResolved(true);
                return Array.get(target, index);
            }
        }
        return null;
    }

    private int toInteger(Object p) {

        if (p instanceof Integer) {
            return ((Integer) p).intValue();
        }
        if (p instanceof Character) {
            return ((Character) p).charValue();
        }
        if (p instanceof Boolean) {
            return ((Boolean) p).booleanValue() ? 1 : 0;
        }
        if (p instanceof Number) {
            return ((Number) p).intValue();
        }
        if (p instanceof String) {
            return Integer.parseInt((String) p);
        }
        throw new IllegalArgumentException();
    }

}
