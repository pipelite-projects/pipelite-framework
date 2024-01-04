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

import java.util.Iterator;
import java.util.List;

import io.pipelite.expression.core.el.ShuntingYardFunctionImpl;
import io.pipelite.expression.core.el.Token;
import io.pipelite.expression.core.el.TokenType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ShuntingYardFunctionImplTest extends ExpressionTestSupport {

    private ShuntingYardFunctionImpl subject;

    @Before
    public void init() {
        subject = new ShuntingYardFunctionImpl(getOperatorRegistry(), getFunctionRegistry());
    }

    @Test
    public void shouldApplyShuntingYard() {
        List<Token> result = subject.apply("1==1");
        Iterator<Token> it = result.iterator();
        Token t1 = it.next();
        Assert.assertNotNull(t1);
        Assert.assertTrue(t1.sameTypeOf(TokenType.LITERAL));
        Assert.assertEquals("1", t1.getSurface());
        Token t2 = it.next();
        Assert.assertTrue(t2.sameTypeOf(TokenType.LITERAL));
        Assert.assertEquals("1", t2.getSurface());
        Token t3 = it.next();
        Assert.assertTrue(t3.sameTypeOf(TokenType.OPERATOR));
        Assert.assertEquals("==", t3.getSurface());
    }

    @Test
    public void shouldApplyShuntingYardOnComplexExpression01() {
        List<Token> result = subject.apply("beanProperty(foo, 'bar') eq 'value'");
        Iterator<Token> it = result.iterator();
        Assert.assertTrue(it.next().sameTypeOf(TokenType.OPEN_BRAKET));
        Assert.assertTrue(it.next().sameTypeOf(TokenType.VARIABLE));
        Assert.assertTrue(it.next().sameTypeOf(TokenType.STRINGPARAM));
        Assert.assertTrue(it.next().sameTypeOf(TokenType.FUNCTION));
        Assert.assertTrue(it.next().sameTypeOf(TokenType.STRINGPARAM));
        Assert.assertTrue(it.next().sameTypeOf(TokenType.OPERATOR));

    }

}
