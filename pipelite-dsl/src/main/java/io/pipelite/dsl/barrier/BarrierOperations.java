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
package io.pipelite.dsl.barrier;

import java.time.Duration;

/**
 * The settings of a barrier, a step that makes the exchange that reaches it wait until the executions of
 * other flows have completed, and then lets it go on unchanged. At least one {@link #await} and a {@link
 * #timeout} are required; mistakes are reported when the flow is defined, not when the first exchange arrives.
 * <p>
 * A key correlates the exchange that waits with the executions it waits for: the same reading of the key is
 * applied to the waiting exchange and to the exchange of the awaited flow.
 */
public interface BarrierOperations {

    /**
     * The key shared by every {@link #await} that does not declare one of its own. Optional: it is enough when
     * all the awaits have a key.
     */
    BarrierOperations correlateBy(CorrelationConfigurator correlation);

    /**
     * Waits for the completion of the flow that reads the queue {@code flowURL} ({@code queue://name}), for the
     * key given to {@link #correlateBy}. May be called more than once, one per flow awaited.
     */
    BarrierOperations await(String flowURL);

    /** As {@link #await(String)}, with a key of its own for this flow. */
    BarrierOperations await(String flowURL, CorrelationConfigurator correlation);

    /** How long an exchange waits, counted from its arrival, before it is given up. */
    BarrierOperations timeout(Duration timeout);
}
