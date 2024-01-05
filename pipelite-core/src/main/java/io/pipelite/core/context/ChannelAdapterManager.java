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
package io.pipelite.core.context;

import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelConfigurer;
import io.pipelite.spi.flow.Flow;

import java.util.Optional;

public interface ChannelAdapterManager {

    void scan();

    void registerChannelAdapter(String channelName, ChannelAdapter channel);

    void addChannelConfigurer(ChannelConfigurer<?> channelConfigurer);

    ChannelAdapter resolveChannel(String channelName);
    Optional<ChannelAdapter> tryResolveChannel(String channelName);

    void notifyFlowRegisterd(Flow flow);
    void notifyContextStarted();
    void notifyContextStopped();

}
