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
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

import io.pipelite.expression.core.ExpressionException;
import io.pipelite.expression.core.context.FunctionRegistry;
import io.pipelite.expression.core.context.OperatorRegistry;
import io.pipelite.expression.core.el.ast.AbstractLazyFunction;
import io.pipelite.expression.core.el.ast.Operator;
import io.pipelite.expression.support.CharacterUtils;
import io.pipelite.common.support.Preconditions;

/**
 * 
 * @author Vincenzo
 * 
 * Expression tokenizer that allows to iterate over a {@link String} expression token by token. Blank characters will be
 * skipped.
 * 
 */
public class Tokenizer implements Iterator<Token> {

    /**
     * The original input expression.
     */
    private final String input;

    /**
     * The Operator registry
     */
    private final OperatorRegistry operatorRegistry;

    /**
     * The Function registry
     */
    private final FunctionRegistry functionRegistry;

    /**
     * Actual position in expression string.
     */
    private int pos = 0;

    /**
     * The previous token or <code>null</code> if none.
     */
    private Token previousToken;

    private Collection<Token> tokens;

    private Iterator<Token> iterator;

    /**
     * Creates a new tokenizer for an expression.
     * 
     * @param input The expression string.
     */
    public Tokenizer(String input, OperatorRegistry operatorRegistry, FunctionRegistry functionRegistry) {
        Preconditions.notNull(input, "Input text is required");
        Preconditions.notNull(operatorRegistry, "OperatorRegistry is required");
        this.input = input.trim();
        this.operatorRegistry = operatorRegistry;
        this.functionRegistry = functionRegistry;
        tokens = parse(this.input);
        iterator = tokens.iterator();
    }

    @Override
    public boolean hasNext() {
        // return (pos < input.length() - 1);
        return iterator.hasNext();
    }

    @Override
    public Token next() {
        return iterator.next();
    }

    @Override
    public void remove() {
        throw new ExpressionException("remove() not supported");
    }

    private Collection<Token> parse(String text) {
        Collection<Token> tokens = new ArrayList<Token>();
        boolean hasToken = true;
        while (hasToken) {
            Token token = nextToken();
            hasToken = token != null;
            if (hasToken) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private Token nextToken() {
        Token token = new Token();
        if (pos >= input.length()) {
            return previousToken = null;
        }
        char ch = input.charAt(pos);
        boolean previousIsWhitespace = false;
        while (Character.isWhitespace(ch) && pos < input.length()) {
            ch = input.charAt(++pos);
            if (!previousIsWhitespace) {
                previousIsWhitespace = true;
            }
        }
        token.setPos(pos);

        boolean isHex = false;
        if (Character.isDigit(ch)) {
            // is a digit
            if (ch == '0' && (CharacterUtils.equalsIgnoreCase(peekNextChar(), 'x'))) {
                // is a hexadecimal
                isHex = true;
            }
            while ((isHex && isHexDigit(ch)) || (Character.isDigit(ch) || ch == Constants.DECIMAL_SEPARATOR
                    || CharacterUtils.equalsIgnoreCase(ch, 'e')
                    || (ch == Constants.MINUS_SIGN && token.length() > 0
                            && (CharacterUtils.equalsIgnoreCase(token.charAt(token.length() - 1), 'e')))
                    || (ch == '+' && token.length() > 0
                            && (CharacterUtils.equalsIgnoreCase(token.charAt(token.length() - 1), 'e'))))
                    && (pos < input.length())) {
                token.append(input.charAt(pos++));
                ch = pos == input.length() ? 0 : input.charAt(pos);
            }
            token.setType(isHex ? TokenType.HEX_LITERAL : TokenType.LITERAL);
        }
        else if (CharacterUtils.isStringDelimiter(ch)) {
            // is a String delimiter
            pos++;
            if (!previousToken.sameTypeOf(TokenType.STRINGPARAM)) {
                ch = input.charAt(pos);
                while (!CharacterUtils.isStringDelimiter(ch)) {
                    token.append(input.charAt(pos++));
                    ch = pos == input.length() ? 0 : input.charAt(pos);
                }
                token.setType(TokenType.STRINGPARAM);
            }
            else {
                return nextToken();
            }
        }
        else if (Braket.isBraket(ch) || CharacterUtils.isComma(ch)) {
            // Is a braket or comma
            if (Braket.isOpenBraket(ch)) {
                token.setType(TokenType.OPEN_BRAKET);
            }
            else if (Braket.isClosedBraket(ch)) {
                token.setType(TokenType.CLOSED_BRAKET);
            }
            else {
                token.setType(TokenType.COMMA);
            }
            token.append(ch);
            pos++;
        }
        else {

            if (Character.isLetter(ch) || Constants.FIRST_VAR_CHARS.indexOf(ch) >= 0) {
                // is a Function or Variable
                while ((Character.isLetter(ch) || CharacterUtils.isLiteralSpecialChar(ch) || Character.isDigit(ch) || Constants.VAR_CHARS.indexOf(ch) >= 0
                        || (token.length() == 0 && Constants.FIRST_VAR_CHARS.indexOf(ch) >= 0)
                                && pos < input.length())) {
                    token.append(input.charAt(pos++));
                    ch = pos == input.length() ? 0 : input.charAt(pos);
                }
                // Remove optional white spaces after function or variable name
                if (CharacterUtils.isSpaceChar(ch)) {
                    while (CharacterUtils.isSpaceChar(ch) && pos < input.length()) {
                        ch = input.charAt(pos++);
                    }
                    pos--;
                }
                TokenType tokenType = null;
                Optional<AbstractLazyFunction> functionHolder = functionRegistry.tryResolveFunction(token.getSurface());
                if (functionHolder.isPresent()) {
                    tokenType = TokenType.FUNCTION;
                }
                else {
                    // Aliased operators eg. eq
                    Optional<Operator> operatorHolder = operatorRegistry.tryResolveOperator(token.getSurface());
                    if (operatorHolder.isPresent()) {
                        tokenType = TokenType.OPERATOR;
                    }
                    else if (Constants.BEAN_PATH_EXPRESSION_CHARS.indexOf(ch) >= 0 && !Braket.isClosedBraket(ch)) {
                        while ((Character.isLetter(ch) || Character.isDigit(ch) || CharacterUtils.isLiteralSpecialChar(ch)
                                || Constants.BEAN_PATH_EXPRESSION_CHARS.indexOf(ch) >= 0) && pos < input.length()) {
                            token.append(input.charAt(pos++));
                            ch = pos == input.length() ? 0 : input.charAt(pos);
                        }
                        tokenType = TokenType.BEAN_PATH_EXPRESSION;
                    }
                    else if (!Braket.isOpenBraket(ch)) {
                        tokenType = TokenType.VARIABLE;
                    }
                }
                token.setType(tokenType);
                // token.setType(Braket.isOpenBraket(ch) ? TokenType.FUNCTION :
                // TokenType.VARIABLE);
            }
            else {
                String greedyMatch = "";
                int initialPos = pos;
                ch = input.charAt(pos);
                int validOperatorSeenUntil = -1;
                while (!Character.isLetter(ch) && !Character.isDigit(ch) && Constants.FIRST_VAR_CHARS.indexOf(ch) < 0
                        && !Character.isWhitespace(ch) && !Braket.isBraket(ch) && !CharacterUtils.isComma(ch)
                        && (pos < input.length())) {
                    greedyMatch += ch;
                    pos++;
                    if (operatorRegistry.containsKey(greedyMatch)) {
                        validOperatorSeenUntil = pos;
                    }
                    ch = pos == input.length() ? 0 : input.charAt(pos);
                }
                if (validOperatorSeenUntil != -1) {
                    token.append(input.substring(initialPos, validOperatorSeenUntil));
                    pos = validOperatorSeenUntil;
                }
                else {
                    token.append(greedyMatch);
                }
                if (previousToken == null || previousToken.sameTypeOf(TokenType.OPERATOR)
                        || previousToken.sameTypeOf(TokenType.OPEN_BRAKET)
                        || previousToken.sameTypeOf(TokenType.COMMA)) {
                    token.append("u");
                    token.setType(TokenType.UNARY_OPERATOR);
                }
                else {
                    token.setType(TokenType.OPERATOR);
                }
            }
        }
        return previousToken = token;
    }

    /**
     * Peek at the next character, without advancing the iterator.
     * 
     * @return The next character or character 0, if at end of string.
     */
    private char peekNextChar() {
        if (pos < (input.length() - 1)) {
            return input.charAt(pos + 1);
        }
        else {
            return 0;
        }
    }

    private boolean isHexDigit(char ch) {
        return ch == 'x' || ch == 'X' || (ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
    }

}
