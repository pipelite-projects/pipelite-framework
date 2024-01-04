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
package io.pipelite.expression.support;

import io.pipelite.common.support.Preconditions;
import io.pipelite.expression.core.context.EvaluationContext;
import io.pipelite.expression.core.context.FunctionRegistry;
import io.pipelite.expression.core.context.OperatorRegistry;
import io.pipelite.expression.core.context.StandardEvaluationContext;
import io.pipelite.expression.core.context.VariableRegistryTreeMapImpl;

public class StandardEvaluationContextFactory implements EvaluationContextFactory {

    private final FunctionRegistry functionRegistry;

    private final OperatorRegistry operatorRegistry;

    public StandardEvaluationContextFactory(FunctionRegistry functionRegistry, OperatorRegistry operatorRegistry) {
        Preconditions.notNull(functionRegistry, "FunctionRegistry is required");
        Preconditions.notNull(operatorRegistry, "OperatorRegistry is required");
        this.functionRegistry = functionRegistry;
        this.operatorRegistry = operatorRegistry;
    }

    @Override
    public EvaluationContext create() {
        return new StandardEvaluationContext(functionRegistry, operatorRegistry, new VariableRegistryTreeMapImpl());
    }

}
