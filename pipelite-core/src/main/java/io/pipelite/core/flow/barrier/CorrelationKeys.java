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
package io.pipelite.core.flow.barrier;

import io.pipelite.dsl.Exchange;
import io.pipelite.dsl.barrier.Correlation;
import io.pipelite.expression.ExpressionParser;

import static io.pipelite.core.flow.ExpressionVariables.HEADERS_VARIABLE_NAME;
import static io.pipelite.core.flow.ExpressionVariables.PAYLOAD_VARIABLE_NAME;

/**
 * Reads the key of a barrier from an exchange, as the user declared it: a header, or an expression of the
 * language of {@code filter} (the variables {@code Payload} and {@code Headers}).
 * <p>
 * The expression engine keeps the variables of an evaluation in the parser and clears them when it ends, so a
 * parser cannot be shared by threads that set their variables before evaluating; each thread has its own. That
 * also keeps the keys of a busy flow from queuing behind the filters of the others on one lock.
 */
public final class CorrelationKeys {

    private static final ThreadLocal<ExpressionParser> PARSER = ThreadLocal.withInitial(ExpressionParser::new);

    private CorrelationKeys() {
    }

    /**
     * The key of {@code exchange}.
     *
     * @throws IllegalStateException when the header is missing, or the expression cannot be evaluated or has no
     *                               value on this exchange: the exchange cannot be correlated
     */
    public static String keyOf(Correlation correlation, Exchange exchange) {
        final String key = switch (correlation.getKind()) {
            // Not tryGetHeader, which only takes a String and throws for any other value: a numeric id is a plausible key.
            case HEADER -> exchange.tryGetHeaderAs(correlation.getText(), Object.class).map(String::valueOf).orElse(null);
            case EXPRESSION -> evaluate(correlation.getText(), exchange);
        };
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(String.format("The exchange has no value for the correlation by %s", correlation));
        }
        return key;
    }

    private static String evaluate(String expression, Exchange exchange) {
        final ExpressionParser parser = PARSER.get();
        final Object payload = exchange.getInputPayload();
        if (payload != null) {
            parser.putVariable(PAYLOAD_VARIABLE_NAME, payload);
        }
        parser.putVariable(HEADERS_VARIABLE_NAME, exchange.getHeaders());
        try {
            return parser.evaluateAsText(expression);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(String.format("The correlation by expression '%s' cannot be evaluated on the exchange: %s",
                expression, exception.getMessage()), exception);
        }
    }
}
