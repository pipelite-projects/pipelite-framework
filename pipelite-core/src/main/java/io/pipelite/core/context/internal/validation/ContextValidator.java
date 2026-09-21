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
package io.pipelite.core.context.internal.validation;

/**
 * One criterion of the startup validation (issue #88), with a single responsibility: reference
 * integrity, source uniqueness, and so on. A validator only reads the {@link ValidationContext}
 * and reports what it finds; it never throws for a finding, so the others still run and the user
 * sees every problem at once.
 * <p>
 * Internal to {@code pipelite-core}: the criteria a context is validated against are not something a
 * user configures. The framework's own are the ones {@code DefaultPipeliteContext} builds its
 * {@link ContextValidatorChain} with; a module of the framework adds one with {@code
 * DefaultPipeliteContext#addContextValidator}, and it runs after those, its findings reported together.
 */
public interface ContextValidator {

    void validate(ValidationContext context, ValidationReport report);

}
