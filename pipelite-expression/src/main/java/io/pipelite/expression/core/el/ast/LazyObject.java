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
package io.pipelite.expression.core.el.ast;

public abstract class LazyObject {

    protected final String text;

    public LazyObject() {
        this(null);
    }

    public LazyObject(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public <T> T evalAs(Class<T> type) {
        Object result = eval();
        if (result == null) {
            return null;
        }
        if (type.isAssignableFrom(result.getClass())) {
            return type.cast(result);
        }
        throw new ClassCastException(String.format("Cannot cast from %s to %s", result.getClass(), type));
    }

    public abstract Object eval();

}
