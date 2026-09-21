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

import io.pipelite.core.context.ContextValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The ordered chain of {@link ContextValidator}s a context is validated against when it starts
 * (issue #88). Unlike a chain of responsibility, every link runs: a finding never stops the rest, so
 * the user sees every problem at once. Warnings are logged, and any error fails the whole chain with
 * a single {@link ContextValidationException}. The chain knows no criterion of its own: whoever
 * builds it decides which validators it holds, and can add more later.
 */
public final class ContextValidatorChain {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    // Copy-on-write: validators are added while the context is being configured, and only read when
    // it starts, so a plain list is not shared between writers and readers at the same time.
    private final List<ContextValidator> validators = new CopyOnWriteArrayList<>();

    public ContextValidatorChain() {
    }

    /**
     * Appends a validator, run after the ones already in the chain, in the order they were added.
     */
    public void add(ContextValidator validator) {
        validators.add(Objects.requireNonNull(validator, "validator is required and cannot be null"));
    }

    /**
     * @throws ContextValidationException if any validator reported an error
     */
    public void validate(ValidationContext context) {

        final ValidationReport report = new ValidationReport();
        for (ContextValidator validator : validators) {
            validator.validate(context, report);
        }

        if (sysLogger.isWarnEnabled()) {
            report.warnings().forEach(warning -> sysLogger.warn("Context validation: {}", warning.describe()));
        }

        final List<ValidationReport.Finding> errors = report.errors();
        if (!errors.isEmpty()) {
            throw new ContextValidationException(errors.stream().map(ValidationReport.Finding::describe).toList());
        }
    }

}
