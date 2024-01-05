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

public class Preconditions {

    public static <T> T notNull(T argument, String errorMessage) {
        if (argument == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        return argument;
    }

    public static void state(boolean state, String errorMessage) {
        if (!state) {
            throw new IllegalStateException(errorMessage);
        }
    }

    public static void hasText(String argument, String errorMessage) {
        if (argument == null || argument.trim().isEmpty()) {
            throw new IllegalArgumentException(errorMessage);
        }
    }

}
