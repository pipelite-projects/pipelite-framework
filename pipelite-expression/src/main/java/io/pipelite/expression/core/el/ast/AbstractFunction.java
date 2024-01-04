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

import java.math.BigDecimal;
import java.util.List;

import io.pipelite.expression.core.ExpressionException;
import io.pipelite.common.support.Preconditions;
import io.pipelite.expression.support.conversion.exception.CannotConvertValueException;

/**
 * Abstract definition of a supported expression function. A function is defined by a name, the number of parameters and
 * the actual processing implementation.
 */
public abstract class AbstractFunction extends AbstractLazyFunction {

    public AbstractFunction(String name, Type inputType, Type outputType, int numParams) {
        super(name, inputType, outputType, numParams);
    }

    @Override
    public LazyObject lazyEval(final LazyParams lazyParams) {
        return new FunctionResult(getName(), this, lazyParams);
    }

    public Object eval(List<Object> parameters) {
        Class<?> expectedInputType = getExpectedInputType();
        for (Object object : parameters) {
            if (object == null) {
                continue;
            }
            if (!expectedInputType.isAssignableFrom(object.getClass())) {
                Preconditions.notNull(conversionService, "ConversionService is required");
                if (conversionService.canConvert(object.getClass(), expectedInputType)) {
                    object = conversionService.convert(object, expectedInputType);
                }
                else {
                    throw new ExpressionException(new CannotConvertValueException(
                            String.format("Cannot convert from %s to %s", object.getClass(), expectedInputType)));
                }
            }
        }
        return doEvaluation(parameters);
    }

    /**
     * Implementation for this function.
     *
     * @param parameters Parameters will be passed by the expression evaluator as a {@link List} of {@link BigDecimal}
     * values.
     * @return The function must return a new {@link BigDecimal} value as a computing result.
     */
    public abstract Object doEvaluation(List<Object> parameters);

}
