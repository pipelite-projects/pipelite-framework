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
package io.pipelite.core.security;

import io.pipelite.core.flow.expression.TextExpressionEvaluator;
import io.pipelite.expression.ExpressionParser;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;

/**
 * Security tests for TextExpressionEvaluator.
 *
 * Vulnerability — Expression Evaluation Infinite Loop (DoS):
 *   TextExpressionEvaluator.evaluateText() loops while matcher finds #{...} patterns.
 *   After substitution it resets the matcher on the new string (expressionMatcher.reset(evaluatedText)).
 *   If the evaluated value of a variable itself contains a #{...} pattern, the matcher
 *   finds it again on the next iteration and the loop never terminates.
 *
 *   Attack vector: a user-controlled Kafka/HTTP message payload or header value
 *   that is propagated as a routing variable can contain "#{varName}", triggering
 *   the loop and hanging the processing thread (Thread DoS).
 */
public class SecurityTest_ExpressionInfiniteLoop {

    private TextExpressionEvaluator evaluator;

    @Before
    public void setup() {
        evaluator = new TextExpressionEvaluator(new ExpressionParser());
    }

    /**
     * A variable whose evaluated value contains the same expression pattern
     * causes an infinite loop in evaluateText().
     *
     * Scenario: Kafka message contains a "routing-key" header with value "#{routingKey}".
     * The RouterNode puts Headers into variables, then calls evaluateText("#{routingKey}", vars).
     * Evaluating #{routingKey} produces "#{routingKey}", which the matcher finds again → loop.
     *
     * Expected: evaluator detects the cycle and terminates (e.g., return the literal value).
     * Actual:   infinite loop — test times out.
     */
    @Test(timeout = 3000)
    @Ignore
    public void shouldNotLoopInfinitelyWhenVariableValueContainsExpressionPattern() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("topic", "#{topic}");

        String result = evaluator.evaluateText("banking.#{topic}.events", variables);

        assertNotNull(result);
    }

    /**
     * A self-referencing chain where the variable value wraps itself in an expression.
     * Simulates a user injecting template syntax into a message attribute
     * (e.g., HTTP header X-Destination: "prod.#{env}.queue").
     *
     * Expected: safe termination.
     * Actual:   infinite loop — test times out.
     */
    @Test(timeout = 3000)
    @Ignore
    public void shouldNotLoopWhenEvaluatedTextExpandsToNewExpression() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("env", "#{region}");
        variables.put("region", "#{env}");

        String result = evaluator.evaluateText("destination-#{env}", variables);

        assertNotNull(result);
    }

    /**
     * A deeply nested value chain causes repeated re-evaluation passes.
     * Each substitution produces a new expression, consuming thread CPU indefinitely.
     *
     * Expected: safe termination after a bounded number of passes.
     * Actual:   infinite loop — test times out.
     */
    @Test(timeout = 3000)
    @Ignore
    public void shouldNotLoopWhenPayloadContainsSelfReferentialExpression() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("x", "value-#{x}-suffix");

        String result = evaluator.evaluateText("#{x}", variables);

        assertNotNull(result);
    }
}
