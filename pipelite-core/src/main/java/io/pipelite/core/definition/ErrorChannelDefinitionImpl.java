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
package io.pipelite.core.definition;

import io.pipelite.dsl.definition.ErrorChannelDefinition;
import io.pipelite.spi.endpoint.EndpointURL;

public class ErrorChannelDefinitionImpl implements ErrorChannelDefinition {

    private final EndpointURL endpointURL;

    public ErrorChannelDefinitionImpl(String endpointURL) {
        this.endpointURL = EndpointURL.parse(endpointURL);
    }

    @Override
    public String getEndpointURL() {
        return endpointURL.getResource();
    }

    @Override
    public ChannelType getErrorChannelType() {
        return ChannelType.RETRY_CHANNEL;
    }
}
