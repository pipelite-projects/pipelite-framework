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
package io.pipelite.expression.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.math.BigDecimal;

import org.junit.Ignore;
import org.junit.Test;

import io.pipelite.expression.Expression;
import io.pipelite.expression.core.context.EvaluationContext;

@Ignore
public class TestEval extends ExpressionTestSupport {

    @Test
    public void testsinAB() {
        try {
            Expression expression = newExpression("sin(a+x)");
            expression.evaluateAs(BigDecimal.class, newEvaluationContext());
        }
        catch (ExpressionException e) {
            assertEquals("Unknown operator or function: a", e.getMessage());
        }
    }

    @Test
    public void testInvalidExpressions1() {
        try {
            Expression expression = newExpression("12 18 2");
            expression.evaluateAs(Object.class, newEvaluationContext());
        }
        catch (ExpressionException e) {
            assertEquals("Too many numbers or variables", e.getMessage());
        }
    }

    @Test
    public void testInvalidExpressions3() {
        try {
            Expression expression = newExpression("12 + * 18");
            expression.evaluateAs(Object.class, newEvaluationContext());
        }
        catch (ExpressionException e) {
            assertEquals("Unknown unary operator '*' at position 6", e.getMessage());
        }
    }

    @Test
    public void testInvalidExpressions4() {
        try {
            Expression expression = newExpression("");
            expression.evaluateAs(Object.class, newEvaluationContext());
        }
        catch (ExpressionException e) {
            assertEquals("Empty expression", e.getMessage());
        }
    }

    @Test
    public void testBrackets() {
        EvaluationContext ctx = newEvaluationContext();
        assertEquals(Integer.valueOf(3), newExpression("(1+2)").evaluateAs(Integer.class, ctx));
        assertEquals(Integer.valueOf(3), newExpression("((1+2))").evaluateAs(Integer.class, ctx));
        assertEquals(Integer.valueOf(3), newExpression("(((1+2)))").evaluateAs(Integer.class, ctx));
        assertEquals(Integer.valueOf(9), newExpression("(1+2)*(1+2)").evaluateAs(Integer.class, ctx));
        assertEquals(Integer.valueOf(10), newExpression("(1+2)*(1+2)+1").evaluateAs(Integer.class, ctx));
        assertEquals(Integer.valueOf(12), newExpression("(1+2)*((1+2)+1)").evaluateAs(Integer.class, ctx));
    }

    @Test(expected = RuntimeException.class)
    public void testUnknow1() {
        assertEquals("", newExpression("7#9").evaluateAs(Object.class, newEvaluationContext()));
    }

    @Test(expected = RuntimeException.class)
    public void testUnknow2() {
        assertEquals("", newExpression("123.6*-9.8-7#9").evaluateAs(Object.class, newEvaluationContext()));
    }

    @Test
    public void testSimple() {
        EvaluationContext ctx = newEvaluationContext();
        assertEquals("3", newExpression("1+2").evaluateAs(String.class, ctx));
        assertEquals("2", newExpression("4/2").evaluateAs(String.class, ctx));
        assertEquals("5", newExpression("3+4/2").evaluateAs(String.class, ctx));
        assertEquals("3.5", newExpression("(3+4)/2").evaluateAs(String.class, ctx));
        assertEquals("7.98", newExpression("4.2*1.9").evaluateAs(String.class, ctx));
        assertEquals("2", newExpression("8%3").evaluateAs(String.class, ctx));
        assertEquals("0", newExpression("8%2").evaluateAs(String.class, ctx));
    }

    @Test
    public void testUnaryMinus() throws Exception {
        EvaluationContext ctx = newEvaluationContext();
        assertEquals("-3", newExpression("-3").evaluateAs(String.class, ctx));
        assertEquals("-2", newExpression("-SQRT(4)").evaluateAs(String.class, ctx));
        assertEquals("-12", newExpression("-(5*3+(+10-13))").evaluateAs(String.class, ctx));
        assertEquals("-2.75", newExpression("-2+3/4*-1").evaluateAs(String.class, ctx));
        assertEquals("9", newExpression("-3^2").evaluateAs(String.class, ctx));
        assertEquals("0.5", newExpression("4^-0.5").evaluateAs(String.class, ctx));
        assertEquals("-1.25", newExpression("-2+3/4").evaluateAs(String.class, ctx));
        assertEquals("-1", newExpression("-(3+-4*-1/-2)").evaluateAs(String.class, ctx));
    }

    @Test
    public void testUnaryPlus() throws Exception {
        EvaluationContext ctx = newEvaluationContext();
        assertEquals("3", newExpression("+3").evaluateAs(String.class, ctx));
        assertEquals("4", newExpression("+(3-1+2)").evaluateAs(String.class, ctx));
        assertEquals("4", newExpression("+(3-(+1)+2)").evaluateAs(String.class, ctx));
        assertEquals("9", newExpression("+3^2").evaluateAs(String.class, ctx));
    }

    @Test
    public void testPow() {
        EvaluationContext ctx = newEvaluationContext();
        assertEquals("16", newExpression("2^4").evaluateAs(String.class, ctx));
        assertEquals("256", newExpression("2^8").evaluateAs(String.class, ctx));
        assertEquals("9", newExpression("3^2").evaluateAs(String.class, ctx));
        assertEquals("6.25", newExpression("2.5^2").evaluateAs(String.class, ctx));
        assertEquals("28.34045", newExpression("2.6^3.5").evaluateAs(String.class, ctx));
    }

    @Test
    public void testSqrt() {
        EvaluationContext ctx = newEvaluationContext();
        assertEquals("4", newExpression("SQRT(16)").evaluateAs(String.class, ctx));
        assertEquals("1.4142135", newExpression("SQRT(2)").evaluateAs(String.class, ctx));
        assertEquals(
                "1.41421356237309504880168872420969807856967187537694807317667973799073247846210703885038753432764157273501384623091229702492483605",
                newExpression("SQRT(2)").evaluateAs(String.class, ctx));
        assertEquals("2.2360679", newExpression("SQRT(5)").evaluateAs(String.class, ctx));
        assertEquals("99.3730345", newExpression("SQRT(9875)").evaluateAs(String.class, ctx));
        assertEquals("2.3558437", newExpression("SQRT(5.55)").evaluateAs(String.class, ctx));
        assertEquals("0", newExpression("SQRT(0)").evaluateAs(String.class, ctx));
    }

    @Test
    public void testFunctions() {
        EvaluationContext ctx = newEvaluationContext();
        assertNotSame("1.5", newExpression("Random()").evaluateAs(String.class, ctx));
        assertEquals("0.400349", newExpression("SIN(23.6)").evaluateAs(String.class, ctx));
        assertEquals("8", newExpression("MAX(-7,8)").evaluateAs(String.class, ctx));
        assertEquals("5", newExpression("MAX(3,max(4,5))").evaluateAs(String.class, ctx));
        assertEquals("9.6", newExpression("MAX(3,max(MAX(9.6,-4.2),Min(5,9)))").evaluateAs(String.class, ctx));
        assertEquals("2.302585", newExpression("LOG(10)").evaluateAs(String.class, ctx));
    }

    @Test
    public void testExpectedParameterNumbers() {
        String err = "";
        try {
            Expression expression = newExpression("Random(1)");
            // expression.evaluateAs(String.class);
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("Function Random expected 0 parameters, got 1", err);

        try {
            Expression expression = newExpression("SIN(1, 6)");
            // expression.evaluateAs(String.class);
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("Function SIN expected 1 parameters, got 2", err);
    }

    @Test
    public void testVariableParameterNumbers() {
        String err = "";
        try {
            Expression expression = newExpression("min()");
            // expression.evaluateAs(String.class);
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("MIN requires at least one parameter", err);

        // assertEquals("1",
        // newExpression("min(1)").evaluateAs(String.class));
        // assertEquals("1", newExpression("min(1,
        // 2)").evaluateAs(String.class));
        // assertEquals("1", newExpression("min(1, 2,
        // 3)").evaluateAs(String.class));
        // assertEquals("3", newExpression("max(3, 2,
        // 1)").evaluateAs(String.class));
        // assertEquals("9", newExpression("max(3, 2, 1, 4, 5, 6, 7, 8, 9,
        // 0)").evaluateAs(String.class));
    }

    @Test
    @Ignore
    public void testOrphanedOperators() {
        String err = "";
        try {
            newExpression("/").evaluateAs(String.class, newEvaluationContext());
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("Unknown unary operator '/' at position 1", err);

        err = "";
        try {
            // newExpression("3/").evaluateAs(String.class);
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("Missing parameter(s) for operator /", err);

        err = "";
        try {
            // newExpression("/3").evaluateAs(String.class);
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("Unknown unary operator '/' at position 1", err);

        err = "";
        try {
            // newExpression("SIN(MAX(23,45,12))/").evaluateAs(String.class);
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("Missing parameter(s) for operator /", err);

    }

    @Test(expected = ExpressionException.class)
    public void closeParenAtStartCausesExpressionException() throws Exception {
        // newExpression("(").evaluateAs(String.class);
    }

    @Test
    public void testOrphanedOperatorsInFunctionParameters() {
        String err = "";
        try {
            newExpression("min(/)").evaluateAs(String.class, newEvaluationContext());
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("Unknown unary operator '/' at position 5", err);

        err = "";
        try {
            newExpression("min(3/)").evaluateAs(String.class, newEvaluationContext());
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("Missing parameter(s) for operator / at character position 5", err);

        err = "";
        try {
            newExpression("min(/3)").evaluateAs(String.class, newEvaluationContext());
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("Unknown unary operator '/' at position 5", err);

        err = "";
        try {
            newExpression("SIN(MAX(23,45,12,23.6/))").evaluateAs(String.class, newEvaluationContext());
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("Missing parameter(s) for operator / at character position 21", err);

        err = "";
        try {
            newExpression("SIN(MAX(23,45,12/,23.6))").evaluateAs(String.class, newEvaluationContext());
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("Missing parameter(s) for operator / at character position 16", err);

        err = "";
        try {
            newExpression("SIN(MAX(23,45,>=12,23.6))").evaluateAs(String.class, newEvaluationContext());
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("Unknown unary operator '>=' at position 15", err);

        err = "";
        try {
            newExpression("SIN(MAX(>=23,45,12,23.6))").evaluateAs(String.class, newEvaluationContext());
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }
        assertEquals("Unknown unary operator '>=' at position 9", err);
    }

    @Test
    public void testExtremeFunctionNesting() {
        assertNotSame("1.5", newExpression("Random()").evaluateAs(String.class, newEvaluationContext()));
        assertEquals("0.0002791281",
                newExpression("SIN(SIN(COS(23.6)))").evaluateAs(String.class, newEvaluationContext()));
        assertEquals("-4", newExpression("MIN(0, SIN(SIN(COS(23.6))), 0-MAX(3,4,MAX(0,SIN(1))),10)")
                .evaluateAs(String.class, newEvaluationContext()));
    }

    @Test
    public void testTrigonometry() {
        // assertEquals("0.5",
        // newExpression("SIN(30)").evaluateAs(String.class));
        // assertEquals("0.8660254",
        // newExpression("cos(30)").evaluateAs(String.class));
        // assertEquals("0.5773503",
        // newExpression("TAN(30)").evaluateAs(String.class));
        // assertEquals("5343237000000",
        // newExpression("SINH(30)").evaluateAs(String.class));
        // assertEquals("5343237000000",
        // newExpression("COSH(30)").evaluateAs(String.class));
        // assertEquals("1",
        // newExpression("TANH(30)").evaluateAs(String.class));
        // assertEquals("0.5235988",
        // newExpression("RAD(30)").evaluateAs(String.class));
        // assertEquals("1718.873",
        // newExpression("DEG(30)").evaluateAs(String.class));
        // assertEquals("30",
        // newExpression("atan(0.5773503)").evaluateAs(String.class));
        // assertEquals("30", newExpression("atan2(0.5773503,
        // 1)").evaluateAs(String.class));
        // assertEquals("33.69007", newExpression("atan2(2,
        // 3)").evaluateAs(String.class));
        // assertEquals("146.3099", newExpression("atan2(2,
        // -3)").evaluateAs(String.class));
        // assertEquals("-146.3099", newExpression("atan2(-2,
        // -3)").evaluateAs(String.class));
        // assertEquals("-33.69007", newExpression("atan2(-2,
        // 3)").evaluateAs(String.class));

    }

    @Test
    public void testMinMaxAbs() {
        EvaluationContext ctx = newEvaluationContext();
        assertEquals("3.78787", newExpression("MAX(3.78787,3.78786)").evaluateAs(String.class, ctx));
        assertEquals("3.78787", newExpression("max(3.78786,3.78787)").evaluateAs(String.class, ctx));
        assertEquals("3.78786", newExpression("MIN(3.78787,3.78786)").evaluateAs(String.class, ctx));
        assertEquals("3.78786", newExpression("Min(3.78786,3.78787)").evaluateAs(String.class, ctx));
        assertEquals("2.123", newExpression("aBs(-2.123)").evaluateAs(String.class, ctx));
        assertEquals("2.123", newExpression("abs(2.123)").evaluateAs(String.class, ctx));
    }

    @Test
    public void testRounding() {
        EvaluationContext ctx = newEvaluationContext();
        assertEquals("3.8", newExpression("round(3.78787,1)").evaluateAs(String.class, ctx));
        assertEquals("3.788", newExpression("round(3.78787,3)").evaluateAs(String.class, ctx));
        assertEquals("3.734", newExpression("round(3.7345,3)").evaluateAs(String.class, ctx));
        assertEquals("-3.734", newExpression("round(-3.7345,3)").evaluateAs(String.class, ctx));
        assertEquals("-3.79", newExpression("round(-3.78787,2)").evaluateAs(String.class, ctx));
        assertEquals("123.79", newExpression("round(123.78787,2)").evaluateAs(String.class, ctx));
        assertEquals("3", newExpression("floor(3.78787)").evaluateAs(String.class, ctx));
        assertEquals("4", newExpression("ceiling(3.78787)").evaluateAs(String.class, ctx));
        assertEquals("-3", newExpression("floor(-2.1)").evaluateAs(String.class, ctx));
        assertEquals("-2", newExpression("ceiling(-2.1)").evaluateAs(String.class, ctx));
    }

    @Test
    public void testMathContext() {
        Expression e = null;
        // e = newExpression("2.5/3").setPrecision(2);
        // assertEquals("0.83", e.evaluateAs(String.class));

        // e = newExpression("2.5/3").setPrecision(3);
        // assertEquals("0.833", e.evaluateAs(String.class));

        // e = newExpression("2.5/3").setPrecision(8);
        // assertEquals("0.83333333",
        // e.evaluateAs(String.class));

        // e = newExpression("2.5/3").setRoundingMode(RoundingMode.DOWN);
        // assertEquals("0.8333333",
        // e.evaluateAs(String.class));

        // e = newExpression("2.5/3").setRoundingMode(RoundingMode.UP);
        // assertEquals("0.8333334",
        // e.evaluateAs(String.class));
    }

    @Test(expected = ExpressionException.class)
    public void unknownFunctionsFailGracefully() throws Exception {
        newExpression("unk(1,2,3)").evaluateAs(String.class, newEvaluationContext());
    }

    @Test
    public void unknownOperatorsFailGracefully() throws Exception {
        String err = "";
        try {
            newExpression("a |*| b").evaluateAs(String.class, newEvaluationContext());
        }
        catch (ExpressionException e) {
            err = e.getMessage();
        }

        assertEquals("Unknown operator '|*|' at position 3", err);
    }

    @Test
    public void testNull() {
        Expression e = newExpression("null");
        assertEquals(null, e.evaluateAs(Object.class, newEvaluationContext()));
    }

    @Test
    public void testCalculationWithNull() {
        try {
            newExpression("null+1").evaluateAs(Object.class, newEvaluationContext());
        }
        catch (NullPointerException e) {
        }

        try {
            newExpression("1 + NULL").evaluateAs(Object.class, newEvaluationContext());
        }
        catch (NullPointerException e) {
        }

        try {
            newExpression("round(Null, 1)").evaluateAs(Object.class, newEvaluationContext());
        }
        catch (NullPointerException e) {
        }

    }

    @Test
    public void canEvalHexExpression() throws Exception {
        BigDecimal result = newExpression("0xcafe").evaluateAs(BigDecimal.class, newEvaluationContext());
        assertEquals(BigDecimal.valueOf(51966), result);
    }

    @Test
    public void hexExpressionCanUseUpperCaseCharacters() throws Exception {
        BigDecimal result = newExpression("0XCAFE").evaluateAs(BigDecimal.class, newEvaluationContext());
        assertEquals(BigDecimal.valueOf(51966), result);
    }

    @Test
    public void longHexExpressionWorks() throws Exception {
        BigDecimal result = newExpression("0xcafebabe").evaluateAs(BigDecimal.class, newEvaluationContext());
        assertEquals(BigDecimal.valueOf(3405691582L), result);
    }

    @Test(expected = ExpressionException.class)
    public void hexExpressionDoesNotAllowNonHexCharacters() throws Exception {
        newExpression("0xbaby").evaluateAsText(newEvaluationContext());
    }

    @Test(expected = NumberFormatException.class)
    public void throwsExceptionIfDoesNotContainHexDigits() throws Exception {
        newExpression("0x").evaluateAsText(newEvaluationContext());
    }

    @Test
    public void hexExpressionsEvaluatedAsExpected() throws Exception {
        BigDecimal result = newExpression("0xcafe + 0xbabe").evaluateAs(BigDecimal.class, newEvaluationContext());
        assertEquals(BigDecimal.valueOf(99772), result);
    }

    @Test
    public void testResultZeroStripping() {
        Expression expression = newExpression("200.40000 / 2");
        assertEquals("100.2", expression.evaluateAsText(newEvaluationContext()));
        assertEquals("100.2000", expression.evaluateAsText(newEvaluationContext()));
    }

    @Test
    public void testImplicitMultiplication() {
        Expression expression = newExpression("22(3+1)");
        assertEquals(Integer.valueOf(88), expression.evaluateAs(Integer.class, newEvaluationContext()));

        expression = newExpression("(a+b)(a-b)");

        EvaluationContext ctx = newEvaluationContext();
        ctx.putVariable("a", 1);
        ctx.putVariable("b", 2);
        assertEquals(Integer.valueOf(-3), expression.evaluateAs(Integer.class, ctx));

        expression = newExpression("0xA(a+b)");
        assertEquals(Integer.valueOf(30), expression.evaluateAs(Integer.class, ctx));
    }

}
