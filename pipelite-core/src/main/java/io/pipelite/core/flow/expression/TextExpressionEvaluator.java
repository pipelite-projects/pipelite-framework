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
