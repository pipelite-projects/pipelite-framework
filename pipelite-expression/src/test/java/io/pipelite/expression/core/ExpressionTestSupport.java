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

import io.pipelite.expression.core.el.Tokenizer;
import org.junit.BeforeClass;

import io.pipelite.expression.Expression;
import io.pipelite.expression.FunctionRegistryConfiguration;
import io.pipelite.expression.OperatorRegistryConfiguration;
import io.pipelite.expression.core.context.EvaluationContext;
import io.pipelite.expression.core.context.FunctionRegistry;
import io.pipelite.expression.core.context.FunctionRegistryTreeMapImpl;
import io.pipelite.expression.core.context.OperatorRegistry;
import io.pipelite.expression.core.context.OperatorRegistryTreeMapImpl;
import io.pipelite.expression.core.context.StandardEvaluationContext;
import io.pipelite.expression.core.context.VariableRegistryTreeMapImpl;
import io.pipelite.expression.core.el.bean.ArrayElResolver;
import io.pipelite.expression.core.el.bean.BeanElResolver;
import io.pipelite.expression.core.el.bean.CompositeElResolver;
import io.pipelite.expression.core.el.bean.FieldAccessStrategy;
import io.pipelite.expression.core.el.bean.ListElResolver;
import io.pipelite.expression.core.el.bean.MapElResolver;
import io.pipelite.expression.support.conversion.ConversionService;
import io.pipelite.expression.support.conversion.DefaultConversionService;
import io.pipelite.expression.support.conversion.impl.ConversionConfiguration;
import io.pipelite.expression.support.conversion.impl.ConverterRegistry;
import io.pipelite.expression.support.conversion.impl.DefaultConversionConfiguration;

public abstract class ExpressionTestSupport {

    private static OperatorRegistryTreeMapImpl operatorRegistry;

    private static FunctionRegistryTreeMapImpl functionRegistry;

    private static ConversionService conversionService;

    protected OperatorRegistry getOperatorRegistry() {
        return operatorRegistry;
    }

    protected FunctionRegistry getFunctionRegistry() {
        return functionRegistry;
    }

    protected Expression newExpression(String expression) {
        CompositeElResolver elResolver = new CompositeElResolver();
        elResolver.add(new ArrayElResolver());
        elResolver.add(new ListElResolver());
        elResolver.add(new MapElResolver());
        elResolver.add(new BeanElResolver(new FieldAccessStrategy()));
        return new ExpressionImpl(expression, elResolver, conversionService);
    }

    protected EvaluationContext newEvaluationContext() {
        return new StandardEvaluationContext(functionRegistry, operatorRegistry, new VariableRegistryTreeMapImpl());
    }

    protected Tokenizer newTokenizer(String expression) {
        return new Tokenizer(expression, operatorRegistry, functionRegistry);
    }

    @BeforeClass
    public static void setupTestCase() {
        ConverterRegistry converterRegistry = new ConverterRegistry();
        ConversionConfiguration conversionConfiguration = new DefaultConversionConfiguration();
        conversionConfiguration.configureConverters(converterRegistry);
        conversionService = new DefaultConversionService(converterRegistry);
        operatorRegistry = new OperatorRegistryTreeMapImpl();
        functionRegistry = new FunctionRegistryTreeMapImpl();

        OperatorRegistryConfiguration opConfig = new DefaultOperatorRegistryConfiguration(MathContext.DECIMAL32);
        opConfig.configure(operatorRegistry);

        FunctionRegistryConfiguration funConfig = new DefaultFunctionRegistryConfiguration(conversionService,
                MathContext.DECIMAL32);
        funConfig.configure(functionRegistry);
    }

}
