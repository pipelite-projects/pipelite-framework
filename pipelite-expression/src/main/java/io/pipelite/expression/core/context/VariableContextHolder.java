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
package io.pipelite.expression.core.context;

import io.pipelite.expression.core.context.concurrent.InheritableThreadLocalVariableContextHolderStrategy;
import io.pipelite.expression.core.context.concurrent.VariableContextHolderStrategy;

/**
 * @author vautiero
 *
 */
public class VariableContextHolder {

    private static VariableContextHolderStrategy strategy;

    private VariableContextHolder() {
    }

    static {
        initialize();
    }

    private static void initialize() {
        strategy = new InheritableThreadLocalVariableContextHolderStrategy();
    }

    public static void clearContext() {
        strategy.clearContext();
    }

    public static VariableContext getContext() {
        return strategy.getContext();
    }

    public static void setContext(VariableContext variableContext) {
        strategy.setContext(variableContext);
    }

    public static VariableContext createEmptyContext() {
        return strategy.createEmptyContext();
    }

}
