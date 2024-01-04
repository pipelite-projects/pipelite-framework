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
package io.pipelite.core.flow.process.filter;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.flow.ExpressionVariables;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.process.ProcessContribution;
import io.pipelite.dsl.process.Processor;
import io.pipelite.expression.ExpressionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static io.pipelite.core.flow.ExpressionVariables.HEADERS_VARIABLE_NAME;
import static io.pipelite.core.flow.ExpressionVariables.PAYLOAD_VARIABLE_NAME;

public class ExpressionFilterNode implements Processor {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String expression;
    private final ExpressionParser expressionParser;

    public ExpressionFilterNode(String expression, ExpressionParser expressionParser) {
        Preconditions.hasText(expression, "Illegal expression length");
        Preconditions.notNull(expressionParser, "expressionParser is required and cannot be null");
        this.expression = expression;
        this.expressionParser = expressionParser;
    }

    @Override
    public void process(IOContext ioContext, ProcessContribution contribution) {

        Optional.ofNullable(ioContext.getInputPayload())
            .ifPresent(inputPayload -> expressionParser.putVariable(PAYLOAD_VARIABLE_NAME, inputPayload));
        expressionParser.putVariable(HEADERS_VARIABLE_NAME, ioContext.getHeaders());

        final boolean isSatisfied = expressionParser.evaluateAs(expression, Boolean.class);

        if(!isSatisfied){
            if(logger.isDebugEnabled()){
                logger.debug("Filter expression '{}' not satisfied, payload has been filtered", expression);
            }
            contribution.stopExecution();
        }
    }
}
