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

import java.util.ArrayList;
import java.util.List;

/**
 * Where the validators of a {@link ContextValidatorChain} say what they found. An error stops the
 * context from starting, a warning is only logged.
 */
public final class ValidationReport {

    public enum Severity { ERROR, WARNING }

    /**
     * @param flowName the flow it concerns, or {@code null} for something about the context as a whole
     */
    public record Finding(Severity severity, String flowName, String message) {

        /**
         * The finding as one line of a report: {@code Flow 'name', message}.
         */
        public String describe() {
            return flowName == null ? message : String.format("Flow '%s', %s", flowName, message);
        }
    }

    private final List<Finding> findings = new ArrayList<>();

    public void error(String flowName, String message) {
        findings.add(new Finding(Severity.ERROR, flowName, message));
    }

    public void warn(String flowName, String message) {
        findings.add(new Finding(Severity.WARNING, flowName, message));
    }

    public List<Finding> errors() {
        return of(Severity.ERROR);
    }

    public List<Finding> warnings() {
        return of(Severity.WARNING);
    }

    private List<Finding> of(Severity severity) {
        return findings.stream().filter(finding -> finding.severity() == severity).toList();
    }

}
