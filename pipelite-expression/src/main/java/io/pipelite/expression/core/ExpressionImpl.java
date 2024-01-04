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

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Stack;

import io.pipelite.expression.Expression;
import io.pipelite.expression.core.context.EvaluationContext;
import io.pipelite.expression.core.context.concurrent.ConcurrentEvaluationException;
import io.pipelite.expression.core.el.ShuntingYardFunction;
import io.pipelite.expression.core.el.ShuntingYardFunctionImpl;
import io.pipelite.expression.core.el.Token;
import io.pipelite.expression.core.el.TokenType;
import io.pipelite.expression.core.el.ast.AbstractLazyFunction;
import io.pipelite.expression.core.el.ast.BeanPathExpression;
import io.pipelite.expression.core.el.ast.HexLiteral;
import io.pipelite.expression.core.el.ast.LazyObject;
import io.pipelite.expression.core.el.ast.LazyParams;
import io.pipelite.expression.core.el.ast.Literal;
import io.pipelite.expression.core.el.ast.OperationResult;
import io.pipelite.expression.core.el.ast.Operator;
import io.pipelite.expression.core.el.ast.TextLiteral;
import io.pipelite.expression.core.el.ast.Type;
import io.pipelite.expression.core.el.ast.Variable;
import io.pipelite.expression.core.el.ast.impl.IfFunction;
import io.pipelite.expression.core.el.bean.ElResolver;
import io.pipelite.common.support.Preconditions;
import io.pipelite.expression.support.conversion.ConversionService;
import io.pipelite.expression.support.conversion.exception.CannotConvertValueException;

public class ExpressionImpl implements Expression {

    private final String expression;

    private final ConversionService conversionService;

    private final ElResolver elResolver;

    private ShuntingYardFunction shuntingYardAlgorithm;

    private boolean evaluating;

    /**
     * The cached RPN (Reverse Polish Notation) of the expression.
     */
    private List<Token> rpn = null;

    protected final LazyObject PARAMS_START = new LazyObject() {
        @Override
        public Object eval() {
            return null;
        }
    };

    public ExpressionImpl(String expression, ElResolver elResolver, ConversionService conversionService) {
        Preconditions.notNull(expression, "Expression is required");
        Preconditions.notNull(elResolver, "ElResolver is required");
        Preconditions.notNull(conversionService, "ConversionService is required");
        this.expression = expression;
        this.conversionService = conversionService;
        this.elResolver = elResolver;
    }

    /*
     * @Override public Expression putVariable(String name, Object value) { variableRegistry.put(name.toLowerCase(),
     * value); return this; }
     * 
     * @Override public void clearVariables() { variableRegistry.clear(); }
     */

    @Override
    public String asText() {
        return expression;
    }

    /**
     * Checks whether the expression is a boolean expression. An expression is considered a boolean expression, if the
     * last operator or function is boolean. The IF function is handled special. If the third parameter is boolean, then
     * the IF is also considered boolean, else non-boolean.
     * 
     * @return <code>true</code> if the last operator/function was a boolean.
     */
    public boolean isBoolean(EvaluationContext evaluationContext) {
        List<Token> rpn = getRPN(evaluationContext);
        if (rpn.size() > 0) {
            for (int i = rpn.size() - 1; i >= 0; i--) {
                Token t = rpn.get(i);
                if (t.sameTypeOf(TokenType.FUNCTION)) {
                    Optional<AbstractLazyFunction> functionHolder = evaluationContext
                            .tryResolveFunction(t.getSurface());
                    if (functionHolder.isPresent()) {
                        AbstractLazyFunction function = functionHolder.get();
                        if (IfFunction.class.isAssignableFrom(function.getClass())) {
                            /*
                             * The IF function is handled special. If the third parameter is boolean, then the IF is
                             * also considered a boolean. Just skip the IF function to check the second parameter.
                             */
                            continue;
                        }
                        return function.isBooleanFunction();
                    }
                }
                else if (t.sameTypeOf(TokenType.OPERATOR)) {
                    Operator operator = evaluationContext.tryResolveOperator(t.getSurface()).get();
                    return operator.isBooleanOperator();
                }
            }
        }
        return false;
    }

    public Object evaluate(EvaluationContext evaluationContext) {
        try {
            if (evaluating) {
                throw new ConcurrentEvaluationException();
            }
            evaluating = true;
            return evaluateInternal(evaluationContext);
        }
        finally {
            evaluating = false;
        }
    }

    public <T> T evaluateAs(Class<T> type, EvaluationContext evaluationContext) {
        Object result = evaluate(evaluationContext);
        if (result == null) {
            return null;
        }
        else if (type.isAssignableFrom(result.getClass())) {
            return type.cast(result);
        }
        else if (conversionService.canConvert(result.getClass(), type)) {
            return conversionService.convert(result, type);
        }
        throw new ExpressionException(String.format("Cannot evaluate expression %s as %s", expression, type));
    }

    @Override
    public String evaluateAsText(EvaluationContext evaluationContext) {
        return evaluateAs(String.class, evaluationContext);
    }

    private Object evaluateInternal(EvaluationContext evaluationContext) {
        Stack<LazyObject> stack = new Stack<LazyObject>();
        for (final Token token : getRPN(evaluationContext)) {
            final String surface = token.getSurface();
            switch (token.getType()) {
            case UNARY_OPERATOR: {
                final LazyObject holder = stack.pop();
                Object value = holder.eval();
                Operator operator = tryResolveOperator(surface, evaluationContext).get();
                value = tryConvert(value, operator.getInputType());
                LazyObject result = new OperationResult(operator, value, null);
                stack.push(result);
                break;
            }
            case OPERATOR:
                final LazyObject holder2 = stack.pop();
                final LazyObject holder1 = stack.pop();
                Operator operator = tryResolveOperator(surface, evaluationContext).get();
                Object value1 = tryConvert(holder1.eval(), operator.getInputType());
                Object value2 = tryConvert(holder2.eval(), operator.getInputType());
                stack.push(new OperationResult(operator, value1, value2));
                break;
            case VARIABLE:
                Optional<Variable> variableHolder = tryFindVariable(surface, evaluationContext);
                if (!variableHolder.isPresent()) {
                    throw new ExpressionException("Unknown variable " + token);
                }
                stack.push(variableHolder.get());
                break;
            case BEAN_PATH_EXPRESSION:
                stack.push(new BeanPathExpression(surface, evaluationContext, elResolver));
                break;
            case FUNCTION:
                String functionName = surface.toUpperCase(Locale.ROOT);
                Optional<AbstractLazyFunction> functionHolder = tryResolveFunction(functionName, evaluationContext);
                if (!functionHolder.isPresent()) {
                    throw new IllegalArgumentException(String.format("Cannot resolve function %s", functionName));
                }
                AbstractLazyFunction function = functionHolder.get();
                LazyParams parameters = function.numParamsVaries() ? LazyParams.variableLazyParams()
                        : LazyParams.ofSize(function.getNumParams());
                // pop parameters off the stack until we hit the start of
                // this function's parameter list
                while (!stack.isEmpty() && stack.peek() != PARAMS_START) {
                    parameters.add(0, stack.pop());
                }

                if (stack.peek() == PARAMS_START) {
                    stack.pop();
                }
                LazyObject functionResult = function.lazyEval(parameters);
                stack.push(functionResult);
                break;
            case OPEN_BRAKET:
                stack.push(PARAMS_START);
                break;
            case LITERAL:
                stack.push(new Literal(surface));
                break;
            case STRINGPARAM:
                stack.push(new TextLiteral(surface));
                break;
            case HEX_LITERAL:
                stack.push(new HexLiteral(surface));
                break;
            default:
                break;
            }
        }
        return stack.pop().eval();
    }

    private Object tryConvert(Object source, Type targetType) {
        if (source == null) {
            return null;
        }
        else if (targetType.getJavaType().equals(source.getClass())) {
            return source;
        }
        else {
            if (!targetType.supports(source.getClass())) {
                if (conversionService.canConvert(source.getClass(), targetType.getJavaType())) {
                    return conversionService.convert(source, targetType.getJavaType());
                }
                throw new CannotConvertValueException(
                        String.format("Cannot convert from %s to %s", source.getClass(), targetType.getJavaType()));
            }
            else {
                if (Number.class.isAssignableFrom(source.getClass())) {
                    if (conversionService.canConvert(source.getClass(), Type.NUMERIC.getJavaType())) {
                        return conversionService.convert(source, Type.NUMERIC.getJavaType());
                    }
                }
                return targetType.getJavaType().cast(source);
            }

        }
    }

    /**
     * Cached access to the RPN notation of this expression, ensures only one calculation of the RPN per expression
     * instance. If no cached instance exists, a new one will be created and put to the cache.
     * 
     * @return The cached RPN instance.
     */
    protected List<Token> getRPN(EvaluationContext evaluationContext) {
        if (rpn == null) {
            if (shuntingYardAlgorithm == null) {
                shuntingYardAlgorithm = new ShuntingYardFunctionImpl(evaluationContext.getOperatorRegistry(),
                        evaluationContext.getFunctionRegistry());
            }
            rpn = shuntingYardAlgorithm.apply(this.expression);
            validate(rpn, evaluationContext);
        }
        return rpn;
    }

    protected Optional<Variable> tryFindVariable(String name, EvaluationContext evaluationContext) {
        if (name != null) {
            Optional<Object> valueHolder = evaluationContext.tryResolveVariable(name);
            if (valueHolder.isPresent()) {
                Object value = valueHolder.get();
                return Optional.of(new Variable(name, value));
            }
        }
        return Optional.empty();
    }

    protected Optional<Operator> tryResolveOperator(String key, EvaluationContext evaluationContext) {
        return evaluationContext.tryResolveOperator(key);
    }

    protected Optional<AbstractLazyFunction> tryResolveFunction(String key, EvaluationContext evaluationContext) {
        return evaluationContext.tryResolveFunction(key);
    }

    /**
     * Check that the expression has enough numbers and variables to fit the requirements of the operators and
     * functions, also check for only 1 result stored at the end of the evaluation.
     */
    protected void validate(List<Token> rpn, EvaluationContext evaluationContext) {
        /*-
        * Thanks to Norman Ramsey:
        * http://http://stackoverflow.com/questions/789847/postfix-notation-validation
        */
        // each push on to this stack is a new function scope, with the value of
        // each
        // layer on the stack being the count of the number of parameters in
        // that scope
        Stack<Integer> stack = new Stack<Integer>();

        // push the 'global' scope
        stack.push(0);

        for (final Token token : rpn) {
            String surface = token.getSurface();
            int pos = token.getPos();
            switch (token.getType()) {
            case UNARY_OPERATOR:
                if (stack.peek() < 1) {
                    throw new ExpressionException("Missing parameter(s) for operator " + token);
                }
                break;
            case OPERATOR:
                if (stack.peek() < 2) {
                    throw new ExpressionException("Missing parameter(s) for operator " + token);
                }
                // pop the operator's 2 parameters and add the result
                stack.set(stack.size() - 1, stack.peek() - 2 + 1);
                break;
            case FUNCTION:
                Optional<AbstractLazyFunction> functionHolder = evaluationContext.tryResolveFunction(surface);
                if (!functionHolder.isPresent()) {
                    throw new ExpressionException("Unknown function '" + token + "' at position " + (pos + 1));
                }
                AbstractLazyFunction f = functionHolder.get();
                int numParams = stack.pop();
                if (!f.numParamsVaries() && numParams != f.getNumParams()) {
                    throw new ExpressionException(
                            "Function " + token + " expected " + f.getNumParams() + " parameters, got " + numParams);
                }
                if (stack.size() <= 0) {
                    throw new ExpressionException("Too many function calls, maximum scope exceeded");
                }
                // push the result of the function
                stack.set(stack.size() - 1, stack.peek() + 1);
                break;
            case OPEN_BRAKET:
                stack.push(0);
                break;
            default:
                stack.set(stack.size() - 1, stack.peek() + 1);
            }
        }

        if (stack.size() > 1) {
            throw new ExpressionException("Too many unhandled function parameter lists");
        }
        else if (stack.peek() > 1) {
            throw new ExpressionException("Too many numbers or variables");
        }
        else if (stack.peek() < 1) {
            throw new ExpressionException("Empty expression");
        }
    }

    /**
     * Get a string representation of the RPN (Reverse Polish Notation) for this expression.
     * 
     * @return A string with the RPN representation for this expression.
     */
    public String toRPN(EvaluationContext evaluationContext) {
        StringBuilder result = new StringBuilder();
        for (Token t : getRPN(evaluationContext)) {
            if (result.length() != 0)
                result.append(" ");
            result.append(t.toString());
        }
        return result.toString();
    }

}
