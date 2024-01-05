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

import io.pipelite.common.support.Preconditions;

public class OperatorResult implements LazyNumber {

    private final Operator operator;

    private final LazyNumber leftOperand;

    private final LazyNumber rightOperand;

    public OperatorResult(Operator operator, LazyNumber leftOperand, LazyNumber rightOperand) {
        Preconditions.notNull(operator, "Operator is required");
        Preconditions.notNull(leftOperand, "Left operand is required");
        this.operator = operator;
        this.leftOperand = leftOperand;
        this.rightOperand = rightOperand;
    }

    @Override
    public BigDecimal eval() {
        BigDecimal rightValue = (rightOperand != null) ? rightOperand.eval() : null;
        return rightValue; // operator.eval(leftOperand.eval(), rightValue);
    }

    @Override
    public String getString() {
        // TODO Auto-generated method stub
        return null;
    }

}
