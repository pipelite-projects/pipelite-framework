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

import java.math.MathContext;

import io.pipelite.expression.FunctionRegistryConfiguration;
import io.pipelite.expression.core.context.FunctionRegistry;
import io.pipelite.expression.core.el.ast.impl.BeanPropertyFunction;
import io.pipelite.expression.core.el.ast.impl.IfFunction;
import io.pipelite.expression.core.el.ast.impl.NotFunction;
import io.pipelite.expression.core.el.ast.impl.RoundFunction;
import io.pipelite.expression.core.el.ast.impl.SqrtFunction;
import io.pipelite.common.support.Preconditions;
import io.pipelite.expression.support.conversion.ConversionService;

public class DefaultFunctionRegistryConfiguration implements FunctionRegistryConfiguration {

    private final ConversionService conversionService;

    private final MathContext mathContext;

    public DefaultFunctionRegistryConfiguration(ConversionService conversionService, MathContext mathContext) {
        Preconditions.notNull(conversionService, "ConversionService is required");
        Preconditions.notNull(mathContext, "MathContext is required");
        this.conversionService = conversionService;
        this.mathContext = mathContext;
    }

    @Override
    public FunctionRegistry configure(FunctionRegistry functionRegistry) {
        functionRegistry.register(new NotFunction("NOT").setConversionService(conversionService));
        functionRegistry.register(new IfFunction("IF").setConversionService(conversionService));
        functionRegistry.register(new SqrtFunction("SQRT", mathContext).setConversionService(conversionService));
        functionRegistry.register(new RoundFunction("ROUND", mathContext).setConversionService(conversionService));
        functionRegistry.register(new BeanPropertyFunction("beanProperty").setConversionService(conversionService))
                .withAlias("bp").withAlias("property").withAlias("prop").withAlias("jsonPath");
        return functionRegistry;
    }

}
