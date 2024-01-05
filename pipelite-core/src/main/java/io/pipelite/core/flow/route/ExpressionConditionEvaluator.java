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
package io.pipelite.core.flow.route;

import io.pipelite.common.support.Preconditions;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.route.ExpressionCondition;
import io.pipelite.dsl.route.Condition;
import io.pipelite.dsl.route.ConditionEvaluator;
import io.pipelite.expression.ExpressionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static io.pipelite.core.flow.ExpressionVariables.HEADERS_VARIABLE_NAME;
import static io.pipelite.core.flow.ExpressionVariables.PAYLOAD_VARIABLE_NAME;

public class ExpressionConditionEvaluator implements ConditionEvaluator {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    private final ExpressionParser expressionParser;

    public ExpressionConditionEvaluator(ExpressionParser expressionParser) {
        Objects.requireNonNull(expressionParser, "expressionParser is required and cannot be null");
        this.expressionParser = expressionParser;
    }

    @Override
    public boolean evaluate(Condition condition, IOContext ioContext) {

        Preconditions.notNull(condition, "routeCondition is required and cannot be null");
        Preconditions.state(condition instanceof ExpressionCondition, "routeCondition is not an instance of ExpressionRouteCondition");

        final ExpressionCondition expressionCondition = (ExpressionCondition) condition;
        Preconditions.hasText(expressionCondition.getExpression(), "routeCondition.expression must have text");

        if(ioContext.getInputPayload() != null){
            expressionParser.putVariable(PAYLOAD_VARIABLE_NAME, ioContext.getInputPayload());
        }
        expressionParser.putVariable(HEADERS_VARIABLE_NAME, ioContext.getHeaders());

        try{
            return expressionParser.evaluateAs(expressionCondition.getExpression(), Boolean.class);
        }catch (RuntimeException exception){
            if(sysLogger.isWarnEnabled()){
                sysLogger.warn("An error occurred evaluating expression '{}'",
                    expressionCondition.getExpression(), exception);
            }
            return false;
        }
    }
}
