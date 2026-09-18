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
package io.pipelite.core.definition.builder.retry;

import io.pipelite.core.definition.builder.error.ErrorChannelBuilder;
import io.pipelite.dsl.definition.ErrorChannelDefinition;
import io.pipelite.dsl.definition.builder.Backoff;
import io.pipelite.dsl.definition.builder.ErrorChannelConfigurator;
import io.pipelite.dsl.definition.builder.retry.RetryOperations;
import io.pipelite.dsl.definition.builder.retry.RetryTerminalOperations;
import io.pipelite.dsl.process.ExceptionHandler;

import java.util.Objects;

/**
 * Replaces {@code RetryChannelBuilder} (issue #91): implements both {@link RetryOperations} and
 * {@link RetryTerminalOperations} - the same "one class, two interfaces" shape {@code
 * ErrorChannelBuilder} already uses - so {@code FlowDefinitionBuilder#withRetry(...)} can inspect
 * whichever exhaustion action was declared after {@code configurator.configure(...)} returns.
 */
public class RetryBuilder implements RetryOperations, RetryTerminalOperations {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
    private Backoff backoff;
    private ErrorChannelDefinition errorChannelDefinition;
    private ExceptionHandler exceptionHandler;

    @Override
    public RetryOperations maxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, got " + maxAttempts);
        }
        this.maxAttempts = maxAttempts;
        return this;
    }

    @Override
    public RetryOperations backoff(Backoff backoff) {
        this.backoff = backoff;
        return this;
    }

    @Override
    public RetryTerminalOperations onErrorChannel(ErrorChannelConfigurator configurator) {
        rejectSecondExhaustionAction("onErrorChannel(...)");
        this.errorChannelDefinition = configurator.configure(new ErrorChannelBuilder());
        return this;
    }

    @Override
    public RetryTerminalOperations onExceptionHandler(ExceptionHandler exceptionHandler) {
        rejectSecondExhaustionAction("onExceptionHandler(...)");
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler is required and cannot be null");
        return this;
    }

    private void rejectSecondExhaustionAction(String attempted) {
        if (errorChannelDefinition != null || exceptionHandler != null) {
            throw new IllegalStateException(String.format(
                "%s cannot be declared after %s - a retry has exactly one exhaustion action",
                attempted, errorChannelDefinition != null ? "onErrorChannel(...)" : "onExceptionHandler(...)"));
        }
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public Backoff getBackoff() {
        return backoff;
    }

    public ErrorChannelDefinition getErrorChannelDefinition() {
        return errorChannelDefinition;
    }

    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

}
