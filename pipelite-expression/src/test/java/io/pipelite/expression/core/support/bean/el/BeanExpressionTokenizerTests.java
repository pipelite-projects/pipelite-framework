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
package io.pipelite.expression.core.support.bean.el;

import io.pipelite.expression.core.el.bean.BeanExpressionToken;
import io.pipelite.expression.core.el.bean.BeanExpressionTokenizer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BeanExpressionTokenizerTests {

    @Before
    public void setup() {

    }

    @Test
    public void shouldParseArrayExpression() {
        BeanExpressionTokenizer tokenizer = new BeanExpressionTokenizer("foos[1]");
        BeanExpressionToken token1 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.VARIABLE, token1.getTokenType());
        Assert.assertEquals("foos", token1.getValue());

        BeanExpressionToken token2 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.ARRAY_ACCESS, token2.getTokenType());
        Assert.assertEquals("1", token2.getValue());
        Assert.assertFalse(tokenizer.hasNext());
    }

    @Test
    public void shouldParseMapExpression() {
        BeanExpressionTokenizer tokenizer = new BeanExpressionTokenizer("foos['key1']");
        BeanExpressionToken token1 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.VARIABLE, token1.getTokenType());
        Assert.assertEquals("foos", token1.getValue());

        BeanExpressionToken token2 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.MAP_ACCESS, token2.getTokenType());
        Assert.assertEquals("key1", token2.getValue());
        Assert.assertFalse(tokenizer.hasNext());
    }

    @Test
    public void shouldParseNestedMapExpression() {
        BeanExpressionTokenizer tokenizer = new BeanExpressionTokenizer("foos['key1']['key2']");
        BeanExpressionToken token1 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.VARIABLE, token1.getTokenType());
        Assert.assertEquals("foos", token1.getValue());

        BeanExpressionToken token2 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.MAP_ACCESS, token2.getTokenType());
        Assert.assertEquals("key1", token2.getValue());

        BeanExpressionToken token3 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.MAP_ACCESS, token3.getTokenType());
        Assert.assertEquals("key2", token3.getValue());
        Assert.assertFalse(tokenizer.hasNext());
    }

    @Test
    public void shouldParseListExpression() {
        BeanExpressionTokenizer tokenizer = new BeanExpressionTokenizer("foos(2)");
        BeanExpressionToken token1 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.VARIABLE, token1.getTokenType());
        Assert.assertEquals("foos", token1.getValue());

        BeanExpressionToken token2 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.LIST_ACCESS, token2.getTokenType());
        Assert.assertEquals("2", token2.getValue());
        Assert.assertFalse(tokenizer.hasNext());
    }

    @Test
    public void shouldParseBeanExpression() {
        BeanExpressionTokenizer tokenizer = new BeanExpressionTokenizer("foo.bar");
        BeanExpressionToken token1 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.VARIABLE, token1.getTokenType());
        Assert.assertEquals("foo", token1.getValue());

        BeanExpressionToken token2 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.BEAN_PROPERTY_ACCESS, token2.getTokenType());
        Assert.assertEquals("bar", token2.getValue());
        Assert.assertFalse(tokenizer.hasNext());
    }

    @Test
    public void shouldParseNestedExpressions() {

        BeanExpressionTokenizer tokenizer = new BeanExpressionTokenizer("foos(1).bar.values['value01']");
        BeanExpressionToken token1 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.VARIABLE, token1.getTokenType());
        Assert.assertEquals("foos", token1.getValue());

        BeanExpressionToken token2 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.LIST_ACCESS, token2.getTokenType());
        Assert.assertEquals("1", token2.getValue());

        BeanExpressionToken token3 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.BEAN_PROPERTY_ACCESS, token3.getTokenType());
        Assert.assertEquals("bar", token3.getValue());

        BeanExpressionToken token4 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.BEAN_PROPERTY_ACCESS, token4.getTokenType());
        Assert.assertEquals("values", token4.getValue());

        BeanExpressionToken token5 = tokenizer.next();
        Assert.assertEquals(BeanExpressionToken.TokenType.MAP_ACCESS, token5.getTokenType());
        Assert.assertEquals("value01", token5.getValue());

        Assert.assertFalse(tokenizer.hasNext());
    }

}
