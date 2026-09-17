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
package io.pipelite.spi.channel;

import io.pipelite.dsl.definition.SourceConfigurer;
import io.pipelite.spi.endpoint.Endpoint;

public interface ChannelAdapter {

    Endpoint createEndpoint(String url);
    default void configure(ChannelConfigurer<?> channelConfigurer){}

    default Class<? extends ChannelConfigurer<?>> getChannelConfigurerType(){
        return null;
    }

    /**
     * A fresh, adapter-specific {@link SourceConfigurer} instance for {@code
     * DefaultEndpointFactory} to hand to a {@code fromSource(url, configurer)} callback - {@code
     * null} (the default) means this adapter doesn't support one yet, matching {@link
     * #getChannelConfigurerType()}'s own null-default convention. Unlike {@link ChannelConfigurer},
     * which is shared adapter-wide and supplied by the caller via {@code addChannelConfigurer(...)},
     * this is constructed fresh per {@code fromSource(...)} call, by the adapter itself, since the
     * settings it carries (e.g. a Kafka consumer group id) are per-endpoint, not adapter-wide.
     */
    default SourceConfigurer newSourceConfigurer(){
        return null;
    }

}
