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
package io.pipelite.expression.core.el.ast.impl;

import io.pipelite.expression.core.el.ast.Operator;
import io.pipelite.expression.core.el.ast.Type;

public class EqualsOperator extends Operator {

    public EqualsOperator(String name) {
        super(name, Type.ANY, Type.BOOLEAN, 7, false);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public Object eval(Object arg1, Object arg2) {
        if (arg1 == arg2) {
            return true;
        }
        if (arg1 == null || arg2 == null) {
            return false;
        }

        if (areComparable(arg1, arg2)) {
            return ((Comparable) arg1).compareTo((Comparable) arg2) == 0;
        }
        return arg1.equals(arg2);

    }

    private boolean areComparable(Object arg1, Object arg2) {
        return Comparable.class.isAssignableFrom(arg1.getClass()) && Comparable.class.isAssignableFrom(arg2.getClass());
    }

}
