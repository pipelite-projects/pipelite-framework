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

import io.pipelite.spi.channel.ChannelAdapter;
import io.pipelite.spi.channel.ChannelConfigurer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;

public class FileChannelAdapter implements ChannelAdapter {

    private static final String FILE_RESOURCE_PATTERN =
        "[a-zA-Z0-9_./:@\\\\\\-]+";

    private final FileChannelConfiguration configuration;

    public FileChannelAdapter() {
        this.configuration = new DefaultFileChannelConfiguration();
    }

    @Override
    public void configure(ChannelConfigurer<?> channelConfigurer) {
        ((FileChannelConfigurer) channelConfigurer).configure(configuration);
    }

    @Override
    public Class<? extends ChannelConfigurer<?>> getChannelConfigurerType() {
        return FileChannelConfigurer.class;
    }

    @Override
    public Endpoint createEndpoint(String url) {
        return new FileEndpoint(EndpointURL.parse(url, FILE_RESOURCE_PATTERN), this, configuration);
    }

}
