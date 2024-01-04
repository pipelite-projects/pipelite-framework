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
package io.pipelite.core.flow.expression;

import io.pipelite.common.support.Preconditions;
import io.pipelite.core.flow.ExpressionVariables;
import io.pipelite.core.support.expression.ExpressionUtils;
import io.pipelite.expression.ExpressionParser;

import java.util.Map;
import java.util.regex.Matcher;

public class TextExpressionEvaluator {

    private final ExpressionParser expressionParser;

    public TextExpressionEvaluator(ExpressionParser expressionParser) {
        this.expressionParser = Preconditions.notNull(expressionParser, "expressionParser is required and cannot be null");
    }

    public boolean containsExpressions(String text){
        return ExpressionUtils.hasExpressionText(text);
    }

    public String evaluateText(String text, Map<String,Object> variables){

        String expressionText = new String(text.getBytes());
        synchronized (this){
            final Matcher expressionMatcher = ExpressionVariables.EXPRESSION_PATTERN.matcher(expressionText);
            String evaluatedText = expressionText;
            while (expressionMatcher.find()){
                final String expression = expressionMatcher.group(1);
                variables.forEach(expressionParser::putVariable);
                evaluatedText = expressionParser.evaluateAsText(expression);
                evaluatedText = evaluatedText != null ? evaluatedText : "null";
                final int startIdx = expressionMatcher.start(0);
                final int endIdx = expressionMatcher.end(0);
                evaluatedText = String.format("%s%s%s", expressionText.substring(0, startIdx), evaluatedText, expressionText.substring(endIdx));
                expressionMatcher.reset(evaluatedText);
                expressionText = evaluatedText;
            }
            return evaluatedText;
        }

    }
}
