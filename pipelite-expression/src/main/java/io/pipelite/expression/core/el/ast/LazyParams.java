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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;

public class LazyParams extends ArrayList<LazyObject> {

    private static final long serialVersionUID = 8591039240313516452L;

    public static LazyParams variableLazyParams() {
        return LazyParams.ofSize(0);
    }

    public static LazyParams ofSize(int size) {
        return new LazyParams(size);
    }

    public LazyParams() {
        super();
    }

    public LazyParams(Collection<? extends LazyObject> list) {
        super(list);
    }

    public LazyParams(int size) {
        super(size);
    }

    public Optional<LazyObject> tryGetParam(int index) {
        if (index >= 0 && index < size()) {
            return Optional.ofNullable(get(index));
        }
        return Optional.empty();
    }

}
