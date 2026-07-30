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
package io.pipelite.components.file;

import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.endpoint.DefaultEndpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;
import io.pipelite.spi.flow.concurrent.DefaultThreadFactory;
import io.pipelite.spi.flow.concurrent.FlowNameAbbreviator;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class FileEndpoint extends DefaultEndpoint {

    private final FileChannelConfiguration configuration;

    public FileEndpoint(EndpointURL endpointURL, ChannelAdapter channel, FileChannelConfiguration configuration) {
        super(endpointURL, channel);
        this.configuration = Preconditions.notNull(configuration, "configuration is required and cannot be null");
    }

    @Override
    public Consumer createConsumer() {
        final String mode = getProperties().getOrDefault(FileConstants.MODE_PROPERTY_NAME, FileConstants.MODE_TAIL);
        if (!FileConstants.MODE_TAIL.equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException(String.format(
                "Unsupported file channel mode '%s'; only '%s' is currently supported", mode, FileConstants.MODE_TAIL));
        }
        final FileTailPollingConsumer pollingConsumer = new FileTailPollingConsumer(this, configuration);
        // Built here rather than in the constructor for the same reason as TimeEndpoint: the
        // flow name is only set on pollingConsumer after this method returns, but the factory
        // resolves it lazily, at the first actual thread creation in doStart(), well after that.
        final ThreadFactory threadFactory = new DefaultThreadFactory("file",
            () -> FlowNameAbbreviator.abbreviate(pollingConsumer.getFlowName()));
        return new FileTailConsumerService(pollingConsumer, Executors.newSingleThreadScheduledExecutor(threadFactory), configuration.getStateDirectory());
    }

    @Override
    public Producer createProducer() {
        return new FileProducer(this);
    }

}
