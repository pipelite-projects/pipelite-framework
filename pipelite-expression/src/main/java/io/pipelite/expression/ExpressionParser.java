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
package io.pipelite.expression;

import java.math.MathContext;

import io.pipelite.expression.core.DefaultFunctionRegistryConfiguration;
import io.pipelite.expression.core.DefaultOperatorRegistryConfiguration;
import io.pipelite.expression.core.ExpressionImpl;
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
import io.pipelite.common.support.Preconditions;
import io.pipelite.expression.support.conversion.ConversionService;
import io.pipelite.expression.support.conversion.DefaultConversionService;
import io.pipelite.expression.support.conversion.impl.ConversionConfiguration;
import io.pipelite.expression.support.conversion.impl.ConverterRegistry;
import io.pipelite.expression.support.conversion.impl.DefaultConversionConfiguration;

/**
 * @author Vincenzo Autiero
 *
 */
public class ExpressionParser {

    private static final MathContext DEFAULT_MATH_CONTEXT = MathContext.DECIMAL32;

    private final EvaluationContext evaluationContext;

    private final CompositeElResolver elResolver;

    private final ConversionService conversionService;

    /**
     * 
     */
    public ExpressionParser() {
        this(null, null, new DefaultConversionConfiguration());
    }

    /**
     * @param functionRegistryConfiguration
     * @param operatorRegistryConfiguration
     * @param conversionConfiguration
     */
    public ExpressionParser(FunctionRegistryConfiguration functionRegistryConfiguration,
            OperatorRegistryConfiguration operatorRegistryConfiguration,
            ConversionConfiguration conversionConfiguration) {

        Preconditions.notNull(conversionConfiguration, "ConversionConfiguration is required");

        ConverterRegistry converterRegistry = new ConverterRegistry();
        conversionConfiguration.configureConverters(converterRegistry);
        conversionService = new DefaultConversionService(converterRegistry);

        if (functionRegistryConfiguration == null) {
            functionRegistryConfiguration = new DefaultFunctionRegistryConfiguration(conversionService,
                    DEFAULT_MATH_CONTEXT);
        }
        FunctionRegistry functionRegistry = functionRegistryConfiguration.configure(new FunctionRegistryTreeMapImpl());

        if (operatorRegistryConfiguration == null) {
            operatorRegistryConfiguration = new DefaultOperatorRegistryConfiguration(DEFAULT_MATH_CONTEXT);
        }
        OperatorRegistry operatorRegistry = operatorRegistryConfiguration.configure(new OperatorRegistryTreeMapImpl());

        evaluationContext = new StandardEvaluationContext(functionRegistry, operatorRegistry,
                new VariableRegistryTreeMapImpl());
        elResolver = new CompositeElResolver();
        elResolver.add(new ArrayElResolver());
        elResolver.add(new ListElResolver());
        elResolver.add(new MapElResolver());
        elResolver.add(new BeanElResolver(new FieldAccessStrategy()));
    }

    /**
     * @param name
     * @param value
     */
    public void putVariable(String name, Object value) {
        evaluationContext.putVariable(name, value);
    }

    /**
     * @param expression
     * @return
     */
    public boolean isBooleanExpression(String expression) {
        Expression exp = buildExpression(expression);
        return exp.isBoolean(evaluationContext);
    }

    /**
     * @param expression
     * @return
     */
    public String evaluateAsText(String expression) {
        return evaluateAs(expression, String.class);
    }

    /**
     * @param expression
     * @param type
     * @return
     */
    public <T> T evaluateAs(String expression, Class<T> type) {
        synchronized (this){
            try {
                Expression exp = buildExpression(expression);
                return exp.evaluateAs(type, evaluationContext);
            }
            finally {
                evaluationContext.clear();
            }
        }
    }

    private Expression buildExpression(String expression) {
        Preconditions.hasText(expression, "expression cannot be null or empty");
        return new ExpressionImpl(expression, elResolver, conversionService);

    }

}
