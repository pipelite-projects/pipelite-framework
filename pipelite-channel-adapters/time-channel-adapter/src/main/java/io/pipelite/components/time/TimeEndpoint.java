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
package io.pipelite.components.time;

import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.endpoint.DefaultEndpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;
import io.pipelite.spi.flow.concurrent.DefaultThreadFactory;
import io.pipelite.spi.flow.concurrent.FlowNameAbbreviator;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class TimeEndpoint extends DefaultEndpoint {

    public TimeEndpoint(EndpointURL endpointURL, ChannelAdapter channel) {
        super(endpointURL, channel);
    }

    @Override
    public Consumer createConsumer() {
        // The thread factory is built here, not in the constructor: setFlowName(...) is only
        // called on the Consumer this method returns, after it returns — but the executor's
        // backing thread isn't actually created until doStart() schedules the first task, well
        // after that, so referencing the local pollingConsumer lazily works correctly.
        final TimePollingConsumer pollingConsumer = new TimePollingConsumer(this);
        final ThreadFactory threadFactory = new DefaultThreadFactory("time",
            () -> FlowNameAbbreviator.abbreviate(pollingConsumer.getFlowName()));
        return new TimeService(pollingConsumer, Executors.newSingleThreadScheduledExecutor(threadFactory));
    }

    @Override
    public Producer createProducer() {
        throw new IllegalStateException("TimeComponent doesn't support producers");
    }

}
