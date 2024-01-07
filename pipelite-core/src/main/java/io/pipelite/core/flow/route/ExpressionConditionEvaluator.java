package io.pipelite.core.flow.route;

import io.pipelite.common.support.Preconditions;
import io.pipelite.dsl.IOContext;
import io.pipelite.dsl.route.AlwaysSatisfiedCondition;
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

        if(condition instanceof AlwaysSatisfiedCondition){
            return true;
        }

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
