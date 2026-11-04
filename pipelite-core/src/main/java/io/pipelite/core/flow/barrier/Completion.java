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
package io.pipelite.core.flow.barrier;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * The outcome of the execution of a flow for a key, remembered so that an exchange that arrives after the flow
 * completed does not wait for it. The first outcome of a pair stays: a flow that completed and then fails for the
 * same key (a second execution) does not take the completion back from an exchange that already went on.
 */
public record Completion(Pending of, Outcome outcome, Instant retainUntil) implements Serializable {

    private static final long serialVersionUID = 1L;

    public enum Outcome {
        COMPLETED, FAILED
    }

    public Completion {
        Objects.requireNonNull(of, "of is required and cannot be null");
        Objects.requireNonNull(outcome, "outcome is required and cannot be null");
        Objects.requireNonNull(retainUntil, "retainUntil is required and cannot be null");
    }
}
