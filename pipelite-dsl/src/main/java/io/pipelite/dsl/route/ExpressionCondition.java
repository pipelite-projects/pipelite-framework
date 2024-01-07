package io.pipelite.dsl.route;

import java.util.Objects;

public class ExpressionCondition implements Condition {

    private final String expression;

    public ExpressionCondition(String expression) {
        Objects.requireNonNull(expression, "expression is required and cannot be null");
        this.expression = expression;
    }

    public String getExpression() {
        return expression;
    }
}
