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
package io.pipelite.expression.support;

import io.pipelite.expression.core.el.Constants;

public class CharacterUtils {

    public static boolean equalsIgnoreCase(char left, char right) {
        if (left == right) {
            return true;
        }
        else {
            return Character.toLowerCase(left) == Character.toLowerCase(right);
        }
    }

    public static boolean isSpaceChar(char ch) {
        return ch == ' ';
    }

    public static boolean isComma(char ch) {
        return ch == ',';
    }

    public static boolean isDot(char ch) {
        return ch == '.';
    }

    public static boolean isStringDelimiter(char ch) {
        return ch == '"' || ch == '\'';
    }

    public static boolean isLiteralSpecialChar(char ch){
        return ch == Constants.HYPHEN || ch == Constants.DASH;
    }

}
