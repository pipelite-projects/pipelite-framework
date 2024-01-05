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
package io.pipelite.expression.core.context;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

import io.pipelite.expression.core.el.Constants;
import io.pipelite.expression.core.el.ast.AbstractLazyFunction;
import io.pipelite.expression.core.el.ast.Operator;
import io.pipelite.common.support.Preconditions;

public class StandardEvaluationContext implements EvaluationContext {

    private final FunctionRegistry functionRegistry;

    private final OperatorRegistry operatorRegistry;

    private final VariableRegistry globalVariableRegistry;

    public StandardEvaluationContext(FunctionRegistry functionRegistry, OperatorRegistry operatorRegistry,
            VariableRegistry variableRegistry) {
        Preconditions.notNull(functionRegistry, "FunctionRegistry is required");
        Preconditions.notNull(operatorRegistry, "OperatorRegistry is required");
        Preconditions.notNull(variableRegistry, "VariableRegistry is required");

        this.functionRegistry = functionRegistry;
        this.operatorRegistry = operatorRegistry;
        this.globalVariableRegistry = variableRegistry;

        // Implicit variables
        this.globalVariableRegistry.put("pi", Constants.PI);
        this.globalVariableRegistry.put("e", Constants.e);
        this.globalVariableRegistry.put("null", Constants.NULL);

    }

    @Override
    public void putVariable(String variableName, Object value) {
        getVariableContext().putVariable(variableName, value);
    }

    @Override
    public Set<Entry> entrySet() {
        return getVariableContext().entrySet();
    }

    @Override
    public Iterator<Entry> iterator() {
        return getVariableContext().iterator();
    }

    @Override
    public void clear() {
        getVariableContext().clear();
    }

    @Override
    public VariableContext getVariableContext() {
        return VariableContextHolder.getContext();
    }

    @Override
    public Optional<Object> tryResolveVariable(String variableName) {
        Optional<Object> variableHolder = globalVariableRegistry.tryFindVariable(variableName);
        if (!variableHolder.isPresent()) {
            return VariableContextHolder.getContext().tryResolveVariable(variableName);
        }
        return Optional.empty();
    }

    @Override
    public Optional<AbstractLazyFunction> tryResolveFunction(String functionName) {
        return functionRegistry.tryResolveFunction(functionName);
    }

    @Override
    public Optional<Operator> tryResolveOperator(String operatorName) {
        return operatorRegistry.tryResolveOperator(operatorName);
    }

    @Override
    public VariableRegistry getGlobalVariableRegistry() {
        return globalVariableRegistry;
    }

    @Override
    public OperatorRegistry getOperatorRegistry() {
        return operatorRegistry;
    }

    @Override
    public FunctionRegistry getFunctionRegistry() {
        return functionRegistry;
    }

    @Override
    public String toString() {
        return String.format(
                "EvaluatioContext[\n\tFunctionRegistry: %s\n\tOperatorRegistry: %s\n\tVariableContext: %s\n]",
                functionRegistry, operatorRegistry, VariableContextHolder.getContext());
    }

}
