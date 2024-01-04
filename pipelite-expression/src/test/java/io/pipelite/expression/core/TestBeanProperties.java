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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import io.pipelite.expression.Expression;
import io.pipelite.expression.core.context.EvaluationContext;

public class TestBeanProperties extends ExpressionTestSupport {

    @Test
    public void shouldEvaluateBeanPropertyExpression() {
        EvaluationContext evaluationContext = newEvaluationContext();
        evaluationContext.putVariable("foo", new Foo(new Bar("value")));
        Expression expression = newExpression("beanProperty(foo, 'bar.value')");
        String expected = "value";
        String actual = expression.evaluateAs(String.class, evaluationContext);
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void shouldEvaluateBeanPropertyExpressionAsBoolean() {
        EvaluationContext evaluationContext = newEvaluationContext();
        evaluationContext.putVariable("foo", new Foo(new Bar("value")));
        Expression expression = newExpression("beanProperty(foo, 'bar.value') eq 'value'");
        Assert.assertTrue(expression.evaluateAs(Boolean.class, evaluationContext));
    }

    @Test
    public void shouldEvaluateBeanPropertyExpressionAsInteger() {
        EvaluationContext evaluationContext = newEvaluationContext();
        evaluationContext.putVariable("foo", new Foo(new Bar("value")).setAge(10));
        Expression expression = newExpression("beanProperty(foo, 'age') + 2");
        Assert.assertEquals(Integer.valueOf(12), expression.evaluateAs(Integer.class, evaluationContext));
    }

    @Test
    public void shouldEvaluateComplexBeanPropertyExpression() {
        EvaluationContext evaluationContext = newEvaluationContext();
        evaluationContext.putVariable("foo", new Foo(new Bar("value")).setAge(10));
        Expression expression = newExpression("(beanProperty(foo, 'age') + 2) > 10");
        Assert.assertTrue(expression.evaluateAs(Boolean.class, evaluationContext));
    }

    @Test
    public void shouldEvaluateNestedBeanPropertyExpression() {
        EvaluationContext evaluationContext = newEvaluationContext();
        evaluationContext.putVariable("foo", new Foo(new Bar("bar01", new Bar("child01"))));
        Expression expression = newExpression("beanProperty(foo, 'bar.child.value') eq 'child01'");
        Assert.assertTrue(expression.evaluateAs(Boolean.class, evaluationContext));
    }

    @Test
    public void shouldEvaluateBeanPathExpression() {
        EvaluationContext evaluationContext = newEvaluationContext();
        evaluationContext.putVariable("foo", new Foo(new Bar("value")).setAge(10));
        Expression expression = newExpression("foo.age");
        Assert.assertEquals(Integer.valueOf(10), expression.evaluateAs(Integer.class, evaluationContext));
    }

    @Test
    public void shouldEvaluateListPathExpression() {
        Collection<Foo> foos = new ArrayList<Foo>();
        foos.add(new Foo(new Bar("bar")));
        EvaluationContext evaluationContext = newEvaluationContext();
        evaluationContext.putVariable("foos", foos);

        Expression expression = newExpression("foos(0).bar.value eq 'bar'");
        Assert.assertTrue(expression.evaluateAs(Boolean.class, evaluationContext));
    }

    @Test
    public void shouldEvaluateNestedMapPathExpression() {
        Map<String, Object> map0 = new HashMap<String, Object>();
        Map<String, Object> map1 = new HashMap<String, Object>();
        Map<String, Object> map2 = new HashMap<String, Object>();
        Bar bar = new Bar("bar");
        Foo foo = new Foo(bar);
        foo.setAge(1);
        bar.putData("relatedFoo", foo);
        map2.put("foo", foo);
        map1.put("map2", map2);
        map0.put("map1", map1);

        EvaluationContext evaluationContext = newEvaluationContext();
        evaluationContext.putVariable("map0", map0);

        Expression expression = newExpression("map0['map1']['map2']['foo'].bar.data['relatedFoo'].age eq 1");
        Assert.assertTrue(expression.evaluateAs(Boolean.class, evaluationContext));
    }

}
