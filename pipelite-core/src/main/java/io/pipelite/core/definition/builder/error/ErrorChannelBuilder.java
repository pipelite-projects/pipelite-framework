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
import io.pipelite.dsl.definition.builder.error.DefinedErrorChannelOperations;
import io.pipelite.dsl.definition.builder.error.ErrorChannelOperations;
import io.pipelite.spi.channel.ChannelURL;

import java.util.Objects;

public class ErrorChannelBuilder implements ErrorChannelOperations, DefinedErrorChannelOperations {

    private String flowName;

    @Override
    public DefinedErrorChannelOperations definedFlow(String flowName) {
        Objects.requireNonNull(flowName, "flowName is required and cannot be null");
        final ChannelURL channelURL = ChannelURL.parse(flowName);
        if (channelURL.hasProtocol()) {
            throw new IllegalArgumentException(String.format(
                "definedFlow('%s') expects the plain name of an internal flow (the value passed to " +
                    "Pipelite.defineFlow(...)), not a URL and not a fromSource(...) resource — omit the " +
                    "protocol (e.g. '%s', not '%s://%s'); routing to that flow is applied automatically, " +
                    "and a direct channel adapter (Kafka/HTTP/...) is never a valid dead letter target",
                flowName, channelURL.getEndpointURL(), channelURL.getProtocol(), channelURL.getEndpointURL()));
        }
        this.flowName = flowName;
        return this;
    }

    @Override
    public String getEndpointURL() {
        return flowName;
    }

    @Override
    public ErrorChannelDefinition.ChannelType getErrorChannelType() {
        return ErrorChannelDefinition.ChannelType.DEFINED_CHANNEL;
    }

}
