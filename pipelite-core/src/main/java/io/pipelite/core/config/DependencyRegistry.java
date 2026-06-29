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

import java.util.Optional;

/**
 * A minimal, lookup-only registry of application dependencies, used to resolve the
 * parameters of {@code @DefineFlow} methods in the native (plain-Java) flow configuration SPI.
 *
 * <p>This is deliberately <strong>not</strong> a dependency-injection container: it never
 * constructs an instance of a requested type, never walks a dependency graph, and never
 * manages a lifecycle. It only stores instances supplied by the application ({@link #register})
 * and looks them up ({@link #resolve(Class)}, {@link #resolve(String)}).
 *
 * <p>It is also unrelated to the resolution of {@code FlowNode} (Processor/Consumer/Producer)
 * dependencies, which remains the application's responsibility inside the body of a
 * {@code @DefineFlow} method (plain constructor wiring), not a concern of this registry.
 */
public interface DependencyRegistry {

    /**
     * Registers an instance under the given qualifier name. The type used for by-type
     * resolution ({@link #resolve(Class)}) is derived from {@code instance.getClass()}.
     */
    void register(String name, Object instance);

    /**
     * Resolves the single instance assignable to {@code type}. Returns {@link Optional#empty()}
     * only if no registered instance is assignable to {@code type}; if more than one registered
     * instance is assignable to {@code type}, the ambiguity must be reported as an exception by
     * the implementation rather than resolved arbitrarily.
     */
    Optional<Object> resolve(Class<?> type);

    /**
     * Resolves the instance registered under the given qualifier name.
     */
    Optional<Object> resolve(String name);

}
