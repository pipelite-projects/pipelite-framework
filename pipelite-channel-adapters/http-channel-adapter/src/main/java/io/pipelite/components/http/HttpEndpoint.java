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
package io.pipelite.components.http;

import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.source.Source;

public class HttpEndpoint implements Endpoint {

    private final EndpointURL endpointURL;
    private final HttpChannelAdapter channel;

    public HttpEndpoint(EndpointURL endpointURL, HttpChannelAdapter channel) {
        this.endpointURL = Preconditions.notNull(endpointURL, "endpointURL is required and cannot be null");
        this.channel = Preconditions.notNull(channel, "channel is required and cannot be null");
    }

    @Override
    public EndpointURL getEndpointURL() {
        return endpointURL;
    }

    @Override
    public Source createSource() {

        final HttpSource source = new HttpSource(getProperties().get(HttpConstants.METHOD_PROPERTY_NAME));

        channel.registerSource(endpointURL.getResource(), source);

        return source;

    }

}
