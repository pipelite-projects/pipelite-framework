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

import io.pipelite.expression.core.el.Token;
import io.pipelite.expression.core.el.TokenType;
import io.pipelite.expression.core.el.Tokenizer;
import org.junit.Assert;
import org.junit.Test;

public class TokenizerTest extends ExpressionTestSupport {

    @Test
    public void shouldDetectBasicOperatorName() {
        Tokenizer tokenizer = newTokenizer("1=1");
        Token t1 = tokenizer.next();
        Assert.assertNotNull(t1);
        Assert.assertTrue(t1.sameTypeOf(TokenType.LITERAL));
        Token t2 = tokenizer.next();
        Assert.assertTrue(t2.sameTypeOf(TokenType.OPERATOR));
        Token t3 = tokenizer.next();
        Assert.assertTrue(t3.sameTypeOf(TokenType.LITERAL));
    }

    @Test
    public void shouldDetectOperatorAlias() {
        Tokenizer tokenizer = newTokenizer("1 eq 1");
        Token t1 = tokenizer.next();
        Assert.assertNotNull(t1);
        Assert.assertTrue(t1.sameTypeOf(TokenType.LITERAL));
        Token t2 = tokenizer.next();
        Assert.assertTrue(t2.sameTypeOf(TokenType.OPERATOR));
        Token t3 = tokenizer.next();
        Assert.assertTrue(t3.sameTypeOf(TokenType.LITERAL));
    }

    @Test
    public void shouldDetectBracket() {
        Tokenizer tokenizer = newTokenizer("([{}])");
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPEN_BRAKET));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPEN_BRAKET));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPEN_BRAKET));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.CLOSED_BRAKET));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.CLOSED_BRAKET));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.CLOSED_BRAKET));
    }

    @Test
    public void shouldDetectBeanPropertyFunction() {
        Tokenizer tokenizer = newTokenizer("beanProperty(payload, 'foo')");
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.FUNCTION));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPEN_BRAKET));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.VARIABLE));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.COMMA));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.STRINGPARAM));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.CLOSED_BRAKET));
    }

    @Test
    public void shouldDetectAliasedBeanPropertyFunction() {
        Tokenizer tokenizer = newTokenizer("bp(payload, 'foo')");
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.FUNCTION));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPEN_BRAKET));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.VARIABLE));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.COMMA));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.STRINGPARAM));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.CLOSED_BRAKET));
    }

    @Test
    public void shouldDetectNestedExpressions() {
        Tokenizer tokenizer = newTokenizer("jsonPath(foo, 'bar') eq 'bar'");
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.FUNCTION));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPEN_BRAKET));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.VARIABLE));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.COMMA));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.STRINGPARAM));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.CLOSED_BRAKET));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPERATOR));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.STRINGPARAM));
    }

    @Test
    public void shouldDetectJsonPathFunction() {
        Tokenizer tokenizer = newTokenizer("jsonPath(payload, 'foo') eq 'value'");
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.FUNCTION));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPEN_BRAKET));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.VARIABLE));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.COMMA));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.STRINGPARAM));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.CLOSED_BRAKET));
    }

    @Test
    public void shouldIterateUntilHasNext() {
        Tokenizer tokenizer = newTokenizer("1==1");
        int tokenCount = 0;
        while (tokenizer.hasNext()) {
            tokenCount++;
            Assert.assertNotNull(tokenizer.next());
        }
        Assert.assertEquals(Integer.valueOf(tokenCount), Integer.valueOf(3));
    }

    @Test
    public void shouldDetectBeanPathExpression01() {
        String expression = "foos['foo1'].bar.value";
        Tokenizer tokenizer = newTokenizer(expression);
        Token token = tokenizer.next();
        Assert.assertTrue(token.sameTypeOf(TokenType.BEAN_PATH_EXPRESSION));
        Assert.assertEquals(expression, token.getSurface());
    }

    @Test
    public void shouldDetectBeanPathExpression02() {
        String expression = "foos['foo1'].bar.value eq 'value'";
        Tokenizer tokenizer = newTokenizer(expression);
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.BEAN_PATH_EXPRESSION));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPERATOR));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.STRINGPARAM));
    }

    @Test
    public void shouldDetectBeanPathExpression03() {
        String expression = "foos['foo1'].bar.value eq 'value' and 1 neq 2";
        Tokenizer tokenizer = newTokenizer(expression);
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.BEAN_PATH_EXPRESSION));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPERATOR));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.STRINGPARAM));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPERATOR));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.LITERAL));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPERATOR));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.LITERAL));
    }

    @Test
    public void shouldDetectBeanPathExpression04() {
        String expression = "foos(1).bar.value eq 'value' and 1 neq 2";
        Tokenizer tokenizer = newTokenizer(expression);
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.BEAN_PATH_EXPRESSION));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPERATOR));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.STRINGPARAM));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPERATOR));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.LITERAL));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPERATOR));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.LITERAL));
    }

    @Test
    public void shouldDetectGroupedExpression01() {
        String expression = "(1==1)||(c==a+b)";
        Tokenizer tokenizer = newTokenizer(expression);
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPEN_BRAKET));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.LITERAL));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPERATOR));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.LITERAL));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.CLOSED_BRAKET));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPERATOR));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPEN_BRAKET));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.VARIABLE));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPERATOR));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.VARIABLE));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.OPERATOR));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.VARIABLE));
        Assert.assertTrue(tokenizer.next().sameTypeOf(TokenType.CLOSED_BRAKET));
    }

}
