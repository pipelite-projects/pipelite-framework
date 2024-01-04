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
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;

import io.pipelite.expression.core.ExpressionException;
import io.pipelite.expression.core.el.ast.MathFunction;
import io.pipelite.common.support.Preconditions;

public class SqrtFunction extends MathFunction {

    public SqrtFunction(String name, MathContext mathContext) {
        super(name, 1, mathContext);
    }

    public Object doEvaluation(List<Object> parameters) {
        Preconditions.notNull(parameters.get(0), "A parameter is required");
        /*
         * From The Java Programmers Guide To numerical Computing (Ronald Mak, 2003)
         */

        Object parameter = parameters.get(0);
        BigDecimal x = (BigDecimal) parameter;
        if (x.compareTo(BigDecimal.ZERO) == 0) {
            return new BigDecimal(0);
        }
        if (x.signum() < 0) {
            throw new ExpressionException("Argument to SQRT() function must not be negative");
        }
        BigInteger n = x.movePointRight(mathContext.getPrecision() << 1).toBigInteger();

        int bits = (n.bitLength() + 1) >> 1;
        BigInteger ix = n.shiftRight(bits);
        BigInteger ixPrev;

        do {
            ixPrev = ix;
            ix = ix.add(n.divide(ix)).shiftRight(1);
            // Give other threads a chance to work;
            Thread.yield();
        } while (ix.compareTo(ixPrev) != 0);

        return new BigDecimal(ix, mathContext.getPrecision());
    }

}
