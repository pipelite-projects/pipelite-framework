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
package io.pipelite.core.consumer.internal;

import io.pipelite.core.exchange.internal.ExchangeImpl;

/**
 * Told by a consumer when an execution of its flow starts and when it ends (issue #150), so something outside the
 * flow, the barriers that await it, can follow it without the nodes of the flow knowing.
 * <p>
 * What it returns is what the consumer runs when the execution <em>returned</em>: normally, whatever became of the
 * exchange (it went through, was filtered, or its failure was handed to the error handling). An exception that
 * escapes the flow is not an end: the exchange is not acknowledged and will be delivered again. Whatever it
 * returns must not throw.
 */
public interface ExecutionObserver {

    /** An execution starts for {@code exchange}. Returns what to run when it ends, or {@code null}. */
    Runnable started(ExchangeImpl exchange);
}
