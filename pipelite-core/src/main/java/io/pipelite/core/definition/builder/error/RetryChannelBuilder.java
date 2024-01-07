package io.pipelite.core.definition.builder.error;

import io.pipelite.dsl.definition.builder.retry.RetryChannelOperations;

public class RetryChannelBuilder implements RetryChannelOperations {
    @Override
    public RetryChannelOperations maxAttempts(int maxAttempts) {
        return this;
    }
}
