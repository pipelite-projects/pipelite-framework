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

import java.util.Optional;

public enum Braket {

    OPEN_ROUND('(', true), OPEN_SQUARE('[', true), OPEN_CURLY('{', true), CLOSED_ROUND(')', false), CLOSED_SQUARE(']',
            false), CLOSED_CURLY('}', false);

    private final char symbol;

    private final boolean open;

    private Braket(char symbol, boolean open) {
        this.symbol = symbol;
        this.open = open;
    }

    public char getSymbol() {
        return this.symbol;
    }

    public boolean sameSymbol(char symbol) {
        return this.symbol == symbol;
    }

    public boolean isOpen() {
        return open;
    }

    public static Optional<Braket> tryFind(char symbol) {
        for (Braket braket : values()) {
            if (braket.sameSymbol(symbol)) {
                return Optional.of(braket);
            }
        }
        return Optional.empty();
    }

    public static boolean isBraket(char symbol) {
        return tryFind(symbol).isPresent();
    }

    public static boolean isOpenBraket(char symbol) {
        Optional<Braket> braketHolder = tryFind(symbol);
        if (braketHolder.isPresent()) {
            Braket braket = braketHolder.get();
            return braket.isOpen();
        }
        return false;
        // throw new IllegalArgumentException(String.format("Symbol %s is not a
        // braket", symbol));
    }

    public static boolean isClosedBraket(char symbol) {
        Optional<Braket> braketHolder = tryFind(symbol);
        if (braketHolder.isPresent()) {
            Braket braket = braketHolder.get();
            return !braket.isOpen();
        }
        return false;
    }

}
