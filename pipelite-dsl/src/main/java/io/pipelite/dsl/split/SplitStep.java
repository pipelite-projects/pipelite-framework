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
package io.pipelite.dsl.split;

import io.pipelite.dsl.process.Processor;

import java.util.Objects;

public final class SplitStep {

    private final String name;
    private final Processor processor;

    public SplitStep(String name, Processor processor) {
        Objects.requireNonNull(name, "name is required and cannot be null");
        Objects.requireNonNull(processor, "processor is required and cannot be null");
        this.name = name;
        this.processor = processor;
    }

    public String getName() {
        return name;
    }

    public Processor getProcessor() {
        return processor;
    }
}
