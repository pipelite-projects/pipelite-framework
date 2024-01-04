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
package io.pipelite.common.support;

import java.lang.reflect.Array;

public class ToStringUtils {

    private ToStringUtils() {
        super();
    }

    public static String arrayToString(Object[] array) {
        return arrayToString(array, 10);
    }

    public static String arrayToString(Object[] array, int maxCount) {
        int length = Array.getLength(array);
        StringBuilder str = new StringBuilder(32);
        str.append('[');
        for (int i = 0; i < length; i++) {
            if (i >= maxCount) {
                str.append(",...");
                break;
            }
            if (i > 0) {
                str.append(',');
            }
            str.append(Array.get(array, i));
        }
        str.append(']');
        return str.toString();
    }

}
