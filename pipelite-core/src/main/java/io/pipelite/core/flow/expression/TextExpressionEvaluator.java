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

    /**
     * Single-pass substitution (issue #128): {@code expressionMatcher.find()} only ever walks
     * forward through the original {@code text}, via {@link Matcher#appendReplacement}/{@link
     * Matcher#appendTail} - it never re-scans a value once substituted in. A previous version
     * rebuilt the whole string after each match and reset the matcher on *that*, so a variable
     * whose own value happened to contain {@code #{...}} - self-referential, part of a cycle
     * between two variables, or just coincidentally shaped like one - was found again on the next
     * iteration and evaluated again, forever: a remotely-triggerable thread DoS given any
     * attacker-influenced value flowing into a variable (a Kafka/HTTP header or payload field
     * routed into {@code RouterNode}'s variables, for instance). This shape can't loop: a
     * replacement is only ever written to the output buffer, never fed back into the matcher.
     */
    public String evaluateText(String text, Map<String,Object> variables){
        synchronized (this){
            final Matcher expressionMatcher = ExpressionVariables.EXPRESSION_PATTERN.matcher(text);
            final StringBuilder result = new StringBuilder();
            while (expressionMatcher.find()){
                final String expression = expressionMatcher.group(1);
                // Re-registered on every match, not hoisted above the loop: ExpressionParser#
                // evaluateAs (evaluateAsText's delegate) clears its evaluation context in a
                // finally block after every single evaluation, so a text with more than one
                // #{...} expression would otherwise see empty variables from the second match on.
                variables.forEach(expressionParser::putVariable);
                String evaluatedExpression = expressionParser.evaluateAsText(expression);
                evaluatedExpression = evaluatedExpression != null ? evaluatedExpression : "null";
                expressionMatcher.appendReplacement(result, Matcher.quoteReplacement(evaluatedExpression));
            }
            expressionMatcher.appendTail(result);
            return result.toString();
        }
    }
}
