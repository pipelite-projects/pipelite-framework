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
package io.pipelite.components.slf4j;

import io.pipelite.common.support.Preconditions;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;

public class Slf4jEndpoint implements Endpoint {

    private final EndpointURL endpointURL;

    public Slf4jEndpoint(EndpointURL endpointURL) {
        this.endpointURL = Preconditions.notNull(endpointURL, "endpointURL is required and cannot be null");
    }

    @Override
    public EndpointURL getEndpointURL() {
        return endpointURL;
    }

    @Override
    public Producer createProducer() {
        return new Slf4jProducer(this);
    }

}
