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

import io.pipelite.dsl.process.Processor;
import io.pipelite.test.support.matchers.Action;
import io.pipelite.test.support.matchers.Actions;

/**
 * Exposes the component activations an {@link Action} can perform. Consumed
 * by {@link Action#apply(ExecutionTarget)}; built-in actions are provided by
 * {@link Actions}.
 */
public interface ExecutionTarget {

    /**
     * Executes the given processor against the configured input, in complete
     * isolation — no runtime, no endpoints, fully synchronous.
     */
    ThenOperations process(Processor processor);

    /**
     * Starts a fresh {@code DefaultPipeliteContext} with all registered flows,
     * supplies an exchange to {@code entryPointEndpoint}, waits for the exchange
     * to reach the {@code test://} sink, then stops the context.
     *
     * @param entryPointEndpoint the endpoint name that matches the
     *                           {@code fromSource(...)} of the entry flow
     */
    ThenOperations supplyTo(String entryPointEndpoint);
}
