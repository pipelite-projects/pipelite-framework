package io.pipelite.core.flow.expression;

import io.pipelite.expression.ExpressionParser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class TextExpressionEvaluatorTest {

    private TextExpressionEvaluator subject;

    @Before
    public void setup(){
        subject = new TextExpressionEvaluator(new ExpressionParser());
    }

    @Test
    public void shouldEvaluateSingleExpression(){

        final Map<String,Object> variables = new HashMap<>();
        variables.put("issuerId", "us-bank");
        final String result = subject.evaluateText("banking.#{issuerId}.topic", variables);

        Assert.assertEquals("banking.us-bank.topic", result);

    }

    @Test
    public void shouldEvaluateMultipleExpressions(){

        final Map<String,Object> variables = new HashMap<>();
        variables.put("issuerId", "us-bank");
        variables.put("topicId", "tx-approval");
        variables.put("partitionNumber", 1);
        final String result = subject.evaluateText("banking.#{issuerId}.topic.#{topicId}/#{partitionNumber}", variables);

        Assert.assertEquals("banking.us-bank.topic.tx-approval/1", result);

    }

    @Test
    public void shouldEvaluateNoExpression(){

        final Map<String,Object> variables = new HashMap<>();
        final String result = subject.evaluateText("banking.us-bank.topic.tx-approval/1", variables);

        Assert.assertEquals("banking.us-bank.topic.tx-approval/1", result);

    }
}
