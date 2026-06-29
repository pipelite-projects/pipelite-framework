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
package io.pipelite.core.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefaultDependencyRegistry implements DependencyRegistry {

    private final Map<String, Object> instancesByName = new LinkedHashMap<>();

    @Override
    public void register(String name, Object instance) {
        instancesByName.put(name, instance);
    }

    @Override
    public Optional<Object> resolve(Class<?> type) {

        final List<Map.Entry<String, Object>> matches = instancesByName.entrySet()
            .stream()
            .filter(entry -> type.isInstance(entry.getValue()))
            .collect(Collectors.toList());

        if(matches.isEmpty()){
            return Optional.empty();
        }

        if(matches.size() > 1){
            final List<String> matchingNames = matches.stream()
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            throw new AmbiguousDependencyException(type, matchingNames);
        }

        return Optional.of(matches.get(0).getValue());
    }

    @Override
    public Optional<Object> resolve(String name) {
        return Optional.ofNullable(instancesByName.get(name));
    }

}
