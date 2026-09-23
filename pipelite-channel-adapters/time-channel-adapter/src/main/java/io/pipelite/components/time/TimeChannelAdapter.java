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

import io.pipelite.dsl.definition.SourceConfigurer;
import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;

public class TimeChannelAdapter implements ChannelAdapter {

    @Override
    public Endpoint createEndpoint(String url) {
        return new TimeEndpoint(EndpointURL.parse(url), this);
    }

    /**
     * Defaults {@code durableInbox} to {@code false} (issue #120): a {@code time://} tick's payload
     * is {@code LocalDateTime.now()} at the instant it fires, regenerated on the adapter's own
     * schedule regardless of what a crash destroys - nothing external and irreplaceable is ever at
     * risk the way it is for a source whose messages arrive from outside (HTTP, Kafka, a tailed
     * file), which is what #70's durable inbox exists to protect. A caller with an actual reason to
     * durably track ticks can still opt back in explicitly ({@code fromSource(url, c ->
     * c.durableInbox(true))}) - {@code DefaultEndpointFactory} runs that callback after this
     * pre-seed, so an explicit value always wins.
     */
    @Override
    public SourceConfigurer newSourceConfigurer() {
        final TimeSourceConfigurer configurer = new TimeSourceConfigurer();
        configurer.durableInbox(false);
        return configurer;
    }

}
