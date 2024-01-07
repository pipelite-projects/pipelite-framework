package io.pipelite.expression.core;

import io.pipelite.expression.Expression;
import io.pipelite.expression.core.context.EvaluationContext;
import org.junit.Assert;
import org.junit.Test;

public class TestEnumEval extends ExpressionTestSupport {

    @Test
    public void testEnumEqualsString() {

        final EvaluationContext ctx = newEvaluationContext();
        ctx.putVariable("animalType", AnimalType.DOG);
        Expression e = newExpression("animalType eq 'DOG'");
        final boolean result = e.evaluateAs(Boolean.class, ctx);
        Assert.assertTrue(result);

    }

    @Test
    public void testStringEqualsEnum() {

        final EvaluationContext ctx = newEvaluationContext();
        ctx.putVariable("CAT", "CAT");
        ctx.putVariable("animalType", AnimalType.CAT);
        Expression e = newExpression("CAT eq animalType");
        final boolean result = e.evaluateAs(Boolean.class, ctx);
        Assert.assertTrue(result);

    }

    public enum AnimalType {
        DOG, CAT;
    }
}
