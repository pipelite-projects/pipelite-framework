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

import io.pipelite.common.support.Preconditions;
import io.pipelite.expression.support.conversion.ConversionService;

public abstract class AbstractLazyFunction {

    /**
     * Name of this function.
     */
    private String name;

    /**
     * The {@link Type} of input supported by this function
     */
    private Type inputType;

    /**
     * The {@link Type} of output produced by this function
     */
    private Type outputType;

    /**
     * Number of parameters expected for this function. <code>-1</code> denotes a variable number of parameters.
     */
    private int numParams;

    protected ConversionService conversionService;

    /**
     * Creates a new function with given name and parameter count.
     *
     * @param name The name of the function.
     * @param numParams The number of parameters for this function. <code>-1</code> denotes a variable number of
     * parameters.
     * @param booleanFunction Whether this function is a boolean function.
     */
    public AbstractLazyFunction(String name, Type inputType, Type outputType, int numParams) {
        this.name = name; // .toLowerCase(Locale.ROOT);
        this.inputType = inputType;
        this.outputType = outputType;
        this.numParams = numParams;
    }

    public String getName() {
        return name;
    }

    public int getNumParams() {
        return numParams;
    }

    public boolean numParamsVaries() {
        return numParams < 0;
    }

    public boolean isBooleanFunction() {
        return outputType == Type.BOOLEAN;
    }

    public Class<?> getExpectedInputType() {
        return inputType.getJavaType();
    }

    public boolean supportsInput(Class<?> javaType) {
        return inputType.supports(javaType);
    }

    public AbstractLazyFunction setConversionService(ConversionService conversionService) {
        Preconditions.notNull(conversionService, "ConversionService is required");
        this.conversionService = conversionService;
        return this;
    }

    public abstract LazyObject lazyEval(LazyParams lazyParams);

}
