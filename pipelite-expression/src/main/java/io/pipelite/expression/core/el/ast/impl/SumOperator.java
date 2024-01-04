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

import java.math.BigDecimal;
import java.math.MathContext;

import io.pipelite.expression.core.el.ast.Operator;
import io.pipelite.expression.core.el.ast.Type;
import io.pipelite.common.support.Preconditions;

public class SumOperator extends Operator {

    private final MathContext mathContext;

    public SumOperator(String name, MathContext mathContext) {
        super(name, Type.NUMERIC, Type.NUMERIC, 20, true);
        Preconditions.notNull(mathContext, "MathContext is required");
        this.mathContext = mathContext;
    }

    @Override
    public Object eval(Object arg1, Object arg2) {
        Preconditions.notNull(arg1, "First operand may not be null");
        Preconditions.notNull(arg2, "Second operand may not be null");
        BigDecimal v1 = (BigDecimal) arg1;
        BigDecimal v2 = (BigDecimal) arg2;
        return v1.add(v2, mathContext);
    }

}
