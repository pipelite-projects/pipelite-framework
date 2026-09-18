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

import io.pipelite.dsl.definition.ErrorChannelDefinition;
import io.pipelite.dsl.definition.builder.error.ChannelErrorChannelOperations;
import io.pipelite.dsl.definition.builder.error.DeadLetterQueueErrorChannelOperations;
import io.pipelite.dsl.definition.builder.error.ErrorChannelOperations;

import java.util.Objects;

public class ErrorChannelBuilder implements ErrorChannelOperations, ChannelErrorChannelOperations, DeadLetterQueueErrorChannelOperations {

    private String target;
    private ErrorChannelDefinition.ChannelType channelType;

    /**
     * Renamed and broadened from {@code definedFlow(String)} (issue #91) - no longer rejects a
     * protocol-qualified value; {@code DeadLetterChannelExceptionHandler} branches on {@code
     * ChannelURL.hasProtocol()} at dispatch time instead.
     */
    @Override
    public ChannelErrorChannelOperations toChannel(String target) {
        Objects.requireNonNull(target, "target is required and cannot be null");
        rejectSecondTarget("toChannel(...)");
        this.target = target;
        this.channelType = ErrorChannelDefinition.ChannelType.DEFINED_CHANNEL;
        return this;
    }

    @Override
    public DeadLetterQueueErrorChannelOperations toDLQ() {
        rejectSecondTarget("toDLQ()");
        this.channelType = ErrorChannelDefinition.ChannelType.DEAD_LETTER_QUEUE;
        return this;
    }

    private void rejectSecondTarget(String attempted) {
        if (channelType != null) {
            throw new IllegalStateException(String.format(
                "%s cannot be declared after %s - an error channel has exactly one target",
                attempted, channelType == ErrorChannelDefinition.ChannelType.DEAD_LETTER_QUEUE ? "toDLQ()" : "toChannel(...)"));
        }
    }

    @Override
    public String getEndpointURL() {
        return target;
    }

    @Override
    public ErrorChannelDefinition.ChannelType getErrorChannelType() {
        return channelType;
    }

}
