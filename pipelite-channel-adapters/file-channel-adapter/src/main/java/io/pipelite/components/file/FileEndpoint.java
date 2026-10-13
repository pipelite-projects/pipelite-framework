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
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.endpoint.Producer;
import io.pipelite.spi.source.Source;

public class FileEndpoint implements Endpoint {

    private final EndpointURL endpointURL;
    private final FileChannelConfiguration configuration;

    public FileEndpoint(EndpointURL endpointURL, FileChannelConfiguration configuration) {
        this.endpointURL = Preconditions.notNull(endpointURL, "endpointURL is required and cannot be null");
        this.configuration = Preconditions.notNull(configuration, "configuration is required and cannot be null");
    }

    @Override
    public EndpointURL getEndpointURL() {
        return endpointURL;
    }

    @Override
    public Source createSource() {
        final String mode = getProperties().getOrDefault(FileConstants.MODE_PROPERTY_NAME, FileConstants.MODE_TAIL);
        if (!FileConstants.MODE_TAIL.equalsIgnoreCase(mode)) {
            throw new IllegalArgumentException(String.format(
                "Unsupported file channel mode '%s'; only '%s' is currently supported", mode, FileConstants.MODE_TAIL));
        }
        return new FileTailSource(this, configuration);
    }

    @Override
    public Producer createProducer() {
        return new FileProducer(this, configuration);
    }

}
