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

import java.util.Optional;

import io.pipelite.expression.core.el.ast.AbstractLazyFunction;
import io.pipelite.expression.core.el.ast.BeanProperty;
import io.pipelite.expression.core.el.ast.LazyObject;
import io.pipelite.expression.core.el.ast.LazyParams;
import io.pipelite.expression.core.el.ast.Type;
import io.pipelite.common.support.Preconditions;

public class BeanPropertyFunction extends AbstractLazyFunction {

    public BeanPropertyFunction(String name) {
        super(name, Type.ANY, Type.ANY, -1);
    }

    @Override
    public LazyObject lazyEval(LazyParams lazyParams) {
        Optional<LazyObject> arg0Holder = lazyParams.tryGetParam(0);
        Optional<LazyObject> arg1Holder = lazyParams.tryGetParam(1);
        Preconditions.state(arg0Holder.isPresent(), "Parameter bean is required");
        Preconditions.state(arg1Holder.isPresent(), "Parameter propertyPath is required");

        LazyObject arg0 = arg0Holder.get();
        LazyObject arg1 = arg1Holder.get();
        Object target = arg0.eval();
        String propertyPath = arg1.evalAs(String.class);
        return new BeanProperty(target, propertyPath);
    }

}
