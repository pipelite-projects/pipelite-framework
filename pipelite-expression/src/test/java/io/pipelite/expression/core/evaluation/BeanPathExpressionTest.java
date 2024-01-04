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
package io.pipelite.expression.core.evaluation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import io.pipelite.expression.core.el.ast.BeanPathExpression;
import io.pipelite.expression.support.CharacterUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.pipelite.expression.core.Bar;
import io.pipelite.expression.core.Foo;
import io.pipelite.expression.core.context.SimpleVariableContext;
import io.pipelite.expression.core.context.VariableContext;
import io.pipelite.expression.core.el.bean.ArrayElResolver;
import io.pipelite.expression.core.el.bean.BeanElResolver;
import io.pipelite.expression.core.el.bean.CompositeElResolver;
import io.pipelite.expression.core.el.bean.FieldAccessStrategy;
import io.pipelite.expression.core.el.bean.ListElResolver;
import io.pipelite.expression.core.el.bean.MapElResolver;

public class BeanPathExpressionTest {

    private CompositeElResolver elResolver;

    private VariableContext variableContext;

    @Before
    public void setup() {
        elResolver = new CompositeElResolver();
        elResolver.add(new ArrayElResolver());
        elResolver.add(new ListElResolver());
        elResolver.add(new MapElResolver());
        elResolver.add(new BeanElResolver(new FieldAccessStrategy()));
        variableContext = new SimpleVariableContext();
    }

    @Test
    public void shouldParseVariableExpression() {
        Bar expected = new Bar("bar01");
        variableContext.putVariable("bar", expected);
        BeanPathExpression expression = new BeanPathExpression("bar", variableContext, elResolver);
        Bar actual = expression.evalAs(Bar.class);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void shouldParseBeanPathExpression() {
        variableContext.putVariable("bar", new Bar("bar01"));
        BeanPathExpression expression = new BeanPathExpression("bar.value", variableContext, elResolver);
        String actual = expression.evalAs(String.class);
        Assert.assertEquals("bar01", actual);
    }

    @Test
    public void shouldParseListExpression01() {
        Collection<Bar> bars = new ArrayList<Bar>();
        bars.add(new Bar("bar01"));
        variableContext.putVariable("bars", bars);
        BeanPathExpression expression = new BeanPathExpression("bars(0).value", variableContext, elResolver);
        String actual = expression.evalAs(String.class);
        Assert.assertEquals("bar01", actual);
    }

    @Test
    public void shouldParseMapExpression01() {
        Map<String, Bar> bars = new HashMap<String, Bar>();
        bars.put("key-1", new Bar("bar01"));
        variableContext.putVariable("bars", bars);
        BeanPathExpression expression = new BeanPathExpression("bars['key-1'].value", variableContext, elResolver);
        String actual = expression.evalAs(String.class);
        Assert.assertEquals("bar01", actual);
    }

    @Test
    public void shouldParseArrayExpression01() {
        Bar[] bars = new Bar[1];
        bars[0] = new Bar("bar01");
        variableContext.putVariable("bars", bars);
        BeanPathExpression expression = new BeanPathExpression("bars[0].value", variableContext, elResolver);
        String actual = expression.evalAs(String.class);
        Assert.assertEquals("bar01", actual);
    }

    @Test
    public void shouldParseNestedExpression01() {
        Collection<Foo> foos = new ArrayList<Foo>();
        Bar expected = new Bar("bar01");
        foos.add(new Foo(expected));
        variableContext.putVariable("foos", foos);
        BeanPathExpression expression = new BeanPathExpression("foos(0).bar", variableContext, elResolver);
        Bar actual = expression.evalAs(Bar.class);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void shouldParseNestedExpression02() {
        Collection<Foo> foos = new ArrayList<Foo>();
        foos.add(new Foo(new Bar("bar01")));
        variableContext.putVariable("foos", foos);
        BeanPathExpression expression = new BeanPathExpression("foos(0).bar.value", variableContext, elResolver);
        String actual = expression.evalAs(String.class);
        Assert.assertEquals("bar01", actual);
    }

    @Test
    public void shouldParseNestedExpression03() {
        Collection<Foo> foos = new ArrayList<Foo>();
        foos.add(new Foo(new Bar("bar01", new Bar("childBar"))));
        variableContext.putVariable("foos", foos);
        BeanPathExpression expression = new BeanPathExpression("foos(0).bar.child.value", variableContext, elResolver);
        String actual = expression.evalAs(String.class);
        Assert.assertEquals("childBar", actual);
    }

}
