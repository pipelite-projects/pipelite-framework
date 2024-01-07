package io.pipelite.core.support.expression;

import io.pipelite.core.flow.ExpressionVariables;

public class ExpressionUtils {

    private ExpressionUtils() {}

    public static boolean hasExpressionText(String text){
        return text.contains(ExpressionVariables.EXPRESSION_START_DELIMITER);
    }

}
