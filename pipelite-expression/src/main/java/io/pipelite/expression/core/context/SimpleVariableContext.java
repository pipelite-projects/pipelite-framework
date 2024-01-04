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

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.pipelite.common.support.ToStringBuilder;

/**
 * This implementation holds the context of the variables. It's thread-safe.
 * 
 * @author Vincenzo Autiero
 *
 */
public class SimpleVariableContext implements VariableContext {

    private final Map<String, Object> variableContext;

    public SimpleVariableContext() {
        variableContext = new ConcurrentHashMap<>();
    }

    @Override
    public void putVariable(String name, Object value) {
        variableContext.put(name, value);
    }

    @Override
    public Optional<Object> tryResolveVariable(String variableName) {
        if (variableContext.containsKey(variableName)) {
            return Optional.ofNullable(variableContext.get(variableName));
        }
        return Optional.empty();
    }

    @Override
    public Set<Entry> entrySet() {
        return entryStream().collect(Collectors.<Entry> toSet());
    }

    @Override
    public Iterator<Entry> iterator() {
        return entryStream().iterator();
    }

    @Override
    public void clear() {
        variableContext.clear();
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, false);
        for (Map.Entry<String, Object> entry : variableContext.entrySet()) {
            builder.append(entry.getKey(), entry.getValue());
        }
        return builder.toString();
    }

    private Stream<Entry> entryStream() {
        return variableContext.entrySet().stream().map(new EntryConverter());
    }

    private static class EntryConverter implements Function<Map.Entry<String, Object>, Entry> {
        @Override
        public Entry apply(Map.Entry<String, Object> entry) {
            return new Entry(entry.getKey(), entry.getValue());
        }
    }

}
