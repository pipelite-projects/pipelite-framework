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
import java.util.List;

import io.pipelite.expression.core.el.ast.AbstractFunction;
import io.pipelite.expression.core.el.ast.Type;
import io.pipelite.common.support.Preconditions;

public class NotFunction extends AbstractFunction {

    public NotFunction(String name) {
        super(name, Type.BOOLEAN, Type.BOOLEAN, 1);
    }

    @Override
    public Object doEvaluation(List<Object> parameters) {
        Object parameter = parameters.get(0);
        Preconditions.notNull(parameter, "Parameter at index 0 is required");
        boolean zero = BigDecimal.ZERO.equals(parameter) || Boolean.FALSE.equals(parameter);
        return zero ? Boolean.TRUE : Boolean.FALSE;
    }

}
