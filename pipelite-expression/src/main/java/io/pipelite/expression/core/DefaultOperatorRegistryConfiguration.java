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

import io.pipelite.expression.OperatorRegistryConfiguration;
import io.pipelite.expression.core.context.OperatorRegistry;
import io.pipelite.expression.core.el.ast.impl.AndOperator;
import io.pipelite.expression.core.el.ast.impl.DivisionOperator;
import io.pipelite.expression.core.el.ast.impl.EqualsOperator;
import io.pipelite.expression.core.el.ast.impl.GtOperator;
import io.pipelite.expression.core.el.ast.impl.GteOperator;
import io.pipelite.expression.core.el.ast.impl.LtOperator;
import io.pipelite.expression.core.el.ast.impl.LteOperator;
import io.pipelite.expression.core.el.ast.impl.ModulusOperator;
import io.pipelite.expression.core.el.ast.impl.MultiplicationOperator;
import io.pipelite.expression.core.el.ast.impl.NegativeOperator;
import io.pipelite.expression.core.el.ast.impl.NotEqualsOperator;
import io.pipelite.expression.core.el.ast.impl.OrOperator;
import io.pipelite.expression.core.el.ast.impl.PositiveOperator;
import io.pipelite.expression.core.el.ast.impl.PowerOperator;
import io.pipelite.expression.core.el.ast.impl.SubtractionOperator;
import io.pipelite.expression.core.el.ast.impl.SumOperator;
import io.pipelite.common.support.Preconditions;

public class DefaultOperatorRegistryConfiguration implements OperatorRegistryConfiguration {

    private final MathContext mathContext;

    public DefaultOperatorRegistryConfiguration(MathContext mathContext) {
        Preconditions.notNull(mathContext, "MathContext is required");
        this.mathContext = mathContext;
    }

    @Override
    public OperatorRegistry configure(OperatorRegistry operatorRegistry) {

        operatorRegistry.register(new EqualsOperator("=")).withAlias("==").withAlias("eq");
        operatorRegistry.register(new NotEqualsOperator("!=")).withAlias("<>").withAlias("neq");
        operatorRegistry.register(new AndOperator("&")).withAlias("&&").withAlias("and");
        operatorRegistry.register(new OrOperator("|")).withAlias("||").withAlias("or");
        operatorRegistry.register(new GtOperator(">")).withAlias("gt");
        operatorRegistry.register(new GteOperator(">=")).withAlias("gte");
        operatorRegistry.register(new LtOperator("<")).withAlias("lt");
        operatorRegistry.register(new LteOperator("<=")).withAlias("lte");
        operatorRegistry.register(new PositiveOperator("+"));
        operatorRegistry.register(new NegativeOperator("-"));

        operatorRegistry.register(new SumOperator("+", mathContext));
        operatorRegistry.register(new SubtractionOperator("-", mathContext));
        operatorRegistry.register(new MultiplicationOperator("*", mathContext));
        operatorRegistry.register(new DivisionOperator("/", mathContext));
        operatorRegistry.register(new ModulusOperator("%", mathContext));
        operatorRegistry.register(new PowerOperator("^", mathContext));

        return operatorRegistry;
    }

}
