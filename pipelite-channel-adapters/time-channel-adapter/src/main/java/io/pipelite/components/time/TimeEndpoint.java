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

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class TimeEndpoint extends DefaultEndpoint {

    private final ThreadFactory threadFactory;

    public TimeEndpoint(EndpointURL endpointURL, ChannelAdapter channel) {
        super(endpointURL, channel);
        threadFactory = new DefaultThreadFactory("time");
    }

    @Override
    public Consumer createConsumer() {
        return new TimeService(new TimePollingConsumer(this),
            Executors.newSingleThreadScheduledExecutor(threadFactory));
    }

    @Override
    public Producer createProducer() {
        throw new IllegalStateException("TimeComponent doesn't support producers");
    }

}
