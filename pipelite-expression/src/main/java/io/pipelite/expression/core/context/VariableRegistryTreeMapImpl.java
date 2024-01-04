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

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import io.pipelite.common.support.ToStringBuilder;

public class VariableRegistryTreeMapImpl extends TreeMap<String, Object> implements VariableRegistry {

    private static final long serialVersionUID = 1L;

    public VariableRegistryTreeMapImpl() {
        super(String.CASE_INSENSITIVE_ORDER);
    }

    @Override
    public Optional<Object> tryFindVariable(String name) {
        if (name != null) {
            if (containsKey(name.toLowerCase())) {
                Object value = get(name);
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, false);
        for (Map.Entry<String, Object> entry : this.entrySet()) {
            builder.append(entry.getKey(), entry.getValue());
        }
        return builder.toString();
    }

}
