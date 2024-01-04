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
package io.pipelite.expression.core;

import org.junit.Assert;
import org.junit.Test;

import io.pipelite.expression.Expression;

public class TestBooleans extends ExpressionTestSupport {

    @Test
    public void testIsBoolean() {
        Expression e = newExpression("1==1");
        Assert.assertTrue(e.isBoolean(newEvaluationContext()));

        e = newExpression("a==b");
        Assert.assertTrue(e.isBoolean(newEvaluationContext()));

        e = newExpression("(1==1)||(c==a+b)");
        Assert.assertTrue(e.isBoolean(newEvaluationContext()));

        e = newExpression("(z+z==x-y)||(c==a+b)");
        Assert.assertTrue(e.isBoolean(newEvaluationContext()));

        e = newExpression("NOT(a+b)");
        Assert.assertTrue(e.isBoolean(newEvaluationContext()));

        e = newExpression("a+b");
        Assert.assertFalse(e.isBoolean(newEvaluationContext()));

        e = newExpression("(a==b)+(b==c)");
        Assert.assertFalse(e.isBoolean(newEvaluationContext()));

        e = newExpression("SQRT(2)");
        Assert.assertFalse(e.isBoolean(newEvaluationContext()));

        e = newExpression("SQRT(2) == SQRT(b)");
        Assert.assertTrue(e.isBoolean(newEvaluationContext()));

        e = newExpression("IF(a==b,x+y,x-y)");
        Assert.assertFalse(e.isBoolean(newEvaluationContext()));

        e = newExpression("IF(a==b,x==y,a==b)");
        Assert.assertTrue(e.isBoolean(newEvaluationContext()));

        e = newExpression("jsonPath(payload, 'foo') eq 'value'");
        Assert.assertTrue(e.isBoolean(newEvaluationContext()));
    }

}
