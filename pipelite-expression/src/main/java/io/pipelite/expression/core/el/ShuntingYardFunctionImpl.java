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
package io.pipelite.expression.core.el;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

import io.pipelite.expression.core.ExpressionException;
import io.pipelite.expression.core.context.FunctionRegistry;
import io.pipelite.expression.core.context.OperatorRegistry;
import io.pipelite.expression.core.el.ast.Operator;
import io.pipelite.common.support.Preconditions;

public class ShuntingYardFunctionImpl implements ShuntingYardFunction {

    private final OperatorRegistry operatorRegistry;

    private final FunctionRegistry functionRegistry;

    public ShuntingYardFunctionImpl(OperatorRegistry operatorRegistry, FunctionRegistry functionRegistry) {
        Preconditions.notNull(operatorRegistry, "OperatorRegistry is required");
        Preconditions.notNull(functionRegistry, "FunctionRegistry is required");
        this.operatorRegistry = operatorRegistry;
        this.functionRegistry = functionRegistry;
    }

    @Override
    public List<Token> apply(String expression) {
        List<Token> outputQueue = new ArrayList<Token>();
        Stack<Token> stack = new Stack<Token>();

        Tokenizer tokenizer = new Tokenizer(expression, operatorRegistry, functionRegistry);

        Token lastFunction = null;
        Token previousToken = null;
        while (tokenizer.hasNext()) {
            Token token = tokenizer.next();
            switch (token.getType()) {
            case STRINGPARAM:
                stack.push(token);
                break;
            case LITERAL:
            case HEX_LITERAL:
                outputQueue.add(token);
                break;
            case VARIABLE:
                outputQueue.add(token);
                break;
            case BEAN_PATH_EXPRESSION:
                outputQueue.add(token);
                break;
            case FUNCTION:
                stack.push(token);
                lastFunction = token;
                break;
            case COMMA:
                if (previousToken != null && previousToken.sameTypeOf(TokenType.OPERATOR)) {
                    throw new ExpressionException("Missing parameter(s) for operator " + previousToken
                            + " at character position " + previousToken.getPos());
                }
                while (!stack.isEmpty() && !stack.peek().sameTypeOf(TokenType.OPEN_BRAKET)) {
                    outputQueue.add(stack.pop());
                }
                if (stack.isEmpty()) {
                    throw new ExpressionException("Parse error for function '" + lastFunction + "'");
                }
                break;
            case OPERATOR: {
                if (previousToken != null && (previousToken.sameTypeOf(TokenType.COMMA)
                        || previousToken.sameTypeOf(TokenType.OPEN_BRAKET))) {
                    throw new ExpressionException(
                            "Missing parameter(s) for operator " + token + " at character position " + token.getPos());
                }
                Optional<Operator> opHolder = operatorRegistry.tryResolveOperator(token.getSurface());
                if (!opHolder.isPresent()) {
                    throw new ExpressionException(
                            "Unknown operator '" + token + "' at position " + (token.getPos() + 1));
                }
                Operator op = opHolder.get();
                shuntOperators(outputQueue, stack, op);
                stack.push(token);
                break;
            }
            case UNARY_OPERATOR: {
                if (previousToken != null && !previousToken.sameTypeOf(TokenType.OPERATOR)
                        && !previousToken.sameTypeOf(TokenType.COMMA)
                        && !previousToken.sameTypeOf(TokenType.OPEN_BRAKET)) {
                    throw new ExpressionException("Invalid position for unary operator " + token
                            + " at character position " + token.getPos());
                }
                Optional<Operator> opHolder = operatorRegistry.tryResolveOperator(token.getSurface());
                if (!opHolder.isPresent()) {
                    throw new ExpressionException("Unknown unary operator '"
                            + token.getSurface().substring(0, token.getSurface().length() - 1) + "' at position "
                            + (token.getPos() + 1));
                }
                Operator op = opHolder.get();
                shuntOperators(outputQueue, stack, op);
                stack.push(token);
                break;
            }
            case OPEN_BRAKET:
                if (previousToken != null) {
                    if (previousToken.sameTypeOf(TokenType.LITERAL) || previousToken.sameTypeOf(TokenType.CLOSED_BRAKET)
                            || previousToken.sameTypeOf(TokenType.VARIABLE)
                            || previousToken.sameTypeOf(TokenType.HEX_LITERAL)) {
                        // Implicit multiplication, e.g. 23(a+b) or (a+b)(a-b)
                        Token multiplication = new Token();
                        multiplication.append("*");
                        multiplication.setType(TokenType.OPERATOR);
                        stack.push(multiplication);
                    }
                    // if the ( is preceded by a valid function, then it
                    // denotes the start of a parameter list
                    if (previousToken.sameTypeOf(TokenType.FUNCTION)) {
                        outputQueue.add(token);
                    }
                }
                stack.push(token);
                break;
            case CLOSED_BRAKET:
                if (previousToken != null && previousToken.sameTypeOf(TokenType.OPERATOR)) {
                    throw new ExpressionException("Missing parameter(s) for operator " + previousToken
                            + " at character position " + previousToken.getPos());
                }
                while (!stack.isEmpty() && !stack.peek().sameTypeOf(TokenType.OPEN_BRAKET)) {
                    outputQueue.add(stack.pop());
                }
                if (stack.isEmpty()) {
                    throw new ExpressionException("Mismatched parentheses");
                }
                stack.pop();
                if (!stack.isEmpty() && stack.peek().sameTypeOf(TokenType.FUNCTION)) {
                    outputQueue.add(stack.pop());
                }
            }
            previousToken = token;
        }

        while (!stack.isEmpty()) {
            Token element = stack.pop();
            if (element.sameTypeOf(TokenType.OPEN_BRAKET) || element.sameTypeOf(TokenType.CLOSED_BRAKET)) {
                throw new ExpressionException("Mismatched parentheses");
            }
            outputQueue.add(element);
        }
        return outputQueue;
    }

    private void shuntOperators(List<Token> outputQueue, Stack<Token> stack, Operator o1) {
        Token nextToken = stack.isEmpty() ? null : stack.peek();
        if (nextToken != null) {
            Operator operator = operatorRegistry.get(nextToken.getSurface());
            while (nextToken != null
                    && (nextToken.sameTypeOf(TokenType.OPERATOR) || nextToken.sameTypeOf(TokenType.UNARY_OPERATOR))
                    && ((o1.isLeftAssociative() && o1.getPrecedence() <= operator.getPrecedence())
                            || (o1.getPrecedence() < operator.getPrecedence()))) {
                outputQueue.add(stack.pop());
                nextToken = stack.isEmpty() ? null : stack.peek();
                if (nextToken != null) {
                    operator = operatorRegistry.get(nextToken.getSurface());
                }
            }
        }
    }

}
