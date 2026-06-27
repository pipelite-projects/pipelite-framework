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
package io.pipelite.dsl.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method, declared on a class annotated with {@link FlowConfiguration}, as a
 * factory of a single {@code FlowDefinition}. The method must return a {@code FlowDefinition}
 * (typically built via {@code Pipelite.defineFlow(name)...build()}).
 *
 * <p>Method parameters represent the dependencies required to build the flow: under the
 * native plain-Java scanner they are resolved against the registry's {@code DependencyRegistry};
 * under {@code pipelite-spring-starter} they are resolved via standard Spring autowiring.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DefineFlow {
}
