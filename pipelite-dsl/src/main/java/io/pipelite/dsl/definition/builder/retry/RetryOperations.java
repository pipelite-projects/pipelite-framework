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
package io.pipelite.dsl.definition.builder.retry;

import io.pipelite.dsl.definition.builder.Backoff;
import io.pipelite.dsl.definition.builder.ErrorChannelConfigurator;
import io.pipelite.dsl.process.ExceptionHandler;

/**
 * Replaces {@code RetryChannelOperations} (issue #91): an exhaustion action is now mandatory to
 * even obtain a valid return value - see {@link RetryTerminalOperations}, only reachable through
 * {@link #onErrorChannel}/{@link #onExceptionHandler}.
 */
public interface RetryOperations {

    RetryOperations maxAttempts(int maxAttempts);

    /**
     * The delay to wait between one attempt and the next (issue #95) - {@link Backoff#linear} or
     * {@link Backoff#exponential}. With none declared, a retry runs as soon as the retry channel
     * next polls, same as before this existed.
     */
    RetryOperations backoff(Backoff backoff);

    /**
     * Declares this retry's exhaustion action as routing - to another user-defined flow, a
     * channel adapter directly, or the framework's built-in dead letter queue. See {@link
     * ErrorChannelConfigurator}.
     */
    RetryTerminalOperations onErrorChannel(ErrorChannelConfigurator configurator);

    /**
     * Declares this retry's exhaustion action as invoking a user-supplied {@link
     * ExceptionHandler} instead of routing anywhere.
     */
    RetryTerminalOperations onExceptionHandler(ExceptionHandler exceptionHandler);

}
