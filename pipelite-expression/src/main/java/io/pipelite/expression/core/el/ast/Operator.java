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
package io.pipelite.expression.core.el.ast;

public abstract class Operator {

    /**
     * This operators name (pattern).
     */
    private final String name;

    /**
     * 
     */
    private final Type inputType;

    /**
     * 
     */
    private final Type outputType;

    /**
     * Operators precedence.
     */
    private final int precedence;

    /**
     * Operator is left associative.
     */
    private final boolean leftAssociative;

    public Operator(String name, Type inputType, Type outputType, int precedence, boolean leftAssociative) {
        this.name = name;
        this.inputType = inputType;
        this.outputType = outputType;
        this.precedence = precedence;
        this.leftAssociative = leftAssociative;
    }

    public String getName() {
        return name;
    }

    public Type getInputType() {
        return inputType;
    }

    public Type getOutputType() {
        return outputType;
    }

    public int getPrecedence() {
        return precedence;
    }

    public boolean isLeftAssociative() {
        return leftAssociative;
    }

    public abstract Object eval(Object leftOperand, Object rightOperand);

    public boolean isBooleanOperator() {
        return Type.BOOLEAN == outputType;
    }

    public boolean supportsInput(Type type) {
        return supportsInput(type.getJavaType());
    }

    public boolean supportsInput(Class<?> javaType) {
        return inputType.supports(javaType);
    }

}
