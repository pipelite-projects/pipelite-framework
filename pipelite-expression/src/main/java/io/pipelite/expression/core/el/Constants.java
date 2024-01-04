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

import java.math.BigDecimal;

public class Constants {

    public final static char HASH_SIGN = '#';

    public final static char MINUS_SIGN = '-';

    public final static char DOT = '.';

    public final static char DECIMAL_SEPARATOR = DOT;

    public final static char NULL_CHAR = '\u0000';

    /**
     * The characters (other than letters and digits) allowed as the first character in a variable.
     */
    public final static String FIRST_VAR_CHARS = "_";

    /**
     * The characters (other than letters and digits) allowed as the second or subsequent characters in a variable.
     */
    public final static String VAR_CHARS = "_";

    public final static String BEAN_PATH_EXPRESSION_CHARS = ".'[()]";

    public final static char HYPHEN = '-';

    public final static char DASH = '_';

    /**
     * Definition of PI as a constant, can be used in expressions as variable.
     */
    public final static BigDecimal PI = new BigDecimal(
            "3.1415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679");

    /**
     * Definition of e: "Euler's number" as a constant, can be used in expressions as variable.
     */
    public final static BigDecimal e = new BigDecimal(
            "2.71828182845904523536028747135266249775724709369995957496696762772407663");

    public final static Object NULL = null;

}
