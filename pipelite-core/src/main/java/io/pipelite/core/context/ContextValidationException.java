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
package io.pipelite.core.context;

import java.util.List;

/**
 * Thrown by {@link PipeliteContext#start()} when the flows registered in the context do not fit
 * together (issue #88), for instance a {@code link://} target that no flow declares as its source.
 * It lists every problem found, and is thrown before anything is started: no flow is registered,
 * no consumer runs, no pending durable inbox entry has been recovered.
 */
public class ContextValidationException extends RuntimeException {

    private final List<String> problems;

    public ContextValidationException(List<String> problems) {
        super(describe(problems));
        this.problems = List.copyOf(problems);
    }

    /**
     * One entry per problem, each already naming the flow and the DSL construct it concerns.
     */
    public List<String> getProblems() {
        return problems;
    }

    private static String describe(List<String> problems) {
        final StringBuilder message = new StringBuilder("Context validation failed:");
        for (String problem : problems) {
            message.append("\n  - ").append(problem);
        }
        return message.toString();
    }

}
