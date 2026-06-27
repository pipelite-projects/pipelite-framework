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

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Thrown when {@link DependencyRegistry#resolve(Class)} finds more than one registered
 * instance assignable to the requested type. The registry never picks an arbitrary
 * candidate among them.
 */
public class AmbiguousDependencyException extends RuntimeException {

    public AmbiguousDependencyException(Class<?> requestedType, Collection<String> matchingNames) {
        super(String.format(
            "Ambiguous dependency resolution for type '%s': %d registered instances are assignable (names: %s). " +
                "Resolve by name explicitly instead.",
            requestedType.getName(),
            matchingNames.size(),
            matchingNames.stream().collect(Collectors.joining(", "))));
    }

}
