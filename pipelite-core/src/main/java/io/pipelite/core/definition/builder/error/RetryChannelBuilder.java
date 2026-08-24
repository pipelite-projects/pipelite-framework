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
package io.pipelite.core.definition.builder.error;

import io.pipelite.dsl.definition.builder.retry.RetryChannelOperations;

public class RetryChannelBuilder implements RetryChannelOperations {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private int maxAttempts = DEFAULT_MAX_ATTEMPTS;

    @Override
    public RetryChannelOperations maxAttempts(int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1, got " + maxAttempts);
        }
        this.maxAttempts = maxAttempts;
        return this;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }
}
