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
package io.pipelite.test;

import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.test.support.matchers.Precondition;
import io.pipelite.test.support.matchers.Preconditions;

/**
 * Exposes the test setup a {@link Precondition} can configure. Consumed by
 * {@link Precondition#apply(GivenOperations)}; built-in preconditions are
 * provided by {@link Preconditions}.
 */
public interface GivenOperations {

    void header(String name, Object value);

    void payload(Object payload);

    /**
     * Registers a flow definition to be deployed in the test context.
     * Multiple flows can be chained with {@code link://} between them.
     */
    void flowDefinition(FlowDefinition flowDefinition);

    /**
     * Overrides the default capture timeout (5 s) used by {@code Actions.supplyTo(...)}.
     * Reduce this when testing flows that intentionally stop or filter messages
     * before reaching the {@code test://} sink.
     */
    void timeout(long seconds);
}
