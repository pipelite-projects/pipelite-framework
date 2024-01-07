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
