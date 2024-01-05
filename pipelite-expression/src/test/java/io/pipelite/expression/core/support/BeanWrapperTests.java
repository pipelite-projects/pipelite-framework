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
package io.pipelite.expression.core.support;

import io.pipelite.expression.core.Bar;
import io.pipelite.expression.core.ExpressionTestSupport;
import io.pipelite.expression.core.Foo;
import io.pipelite.expression.core.el.bean.FieldAccessStrategy;
import org.junit.Assert;
import org.junit.Test;

import io.pipelite.expression.support.BeanWrapper;

public class BeanWrapperTests extends ExpressionTestSupport {

    @Test
    public void shouldNavigate2ndLevelPath() {
        Foo target = new Foo(new Bar("bar01"));
        BeanWrapper beanWrapper = BeanWrapper.target(target);
        beanWrapper.setFallbackStrategy(new FieldAccessStrategy());
        String actual = beanWrapper.evaluatePath("bar.value");
        Assert.assertEquals("bar01", actual);
    }

    @Test
    public void shouldNavigate3ndLevelPath() {
        Foo target = new Foo(new Bar("bar01", new Bar("childBar01")));
        BeanWrapper beanWrapper = BeanWrapper.target(target);
        beanWrapper.setFallbackStrategy(new FieldAccessStrategy());
        String actual = beanWrapper.evaluatePath("bar.child.value");
        Assert.assertEquals("childBar01", actual);
    }

}
