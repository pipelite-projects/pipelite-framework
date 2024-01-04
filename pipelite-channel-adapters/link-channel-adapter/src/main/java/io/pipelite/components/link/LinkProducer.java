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
package io.pipelite.components.link;

import io.pipelite.spi.endpoint.DefaultProducer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinkProducer extends DefaultProducer {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    public LinkProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) {

        final LinkChannelAdapter component = endpoint.getChannelAdapter(LinkChannelAdapter.class);
        final EndpointURL endpointURL = endpoint.getEndpointURL();
        component.tryResolveConsumer(endpointURL.getResource())
            .ifPresent(consumer -> {
                if(sysLogger.isDebugEnabled()){
                    sysLogger.debug("Redirecting exchange to '{}'", endpointURL.getResource());
                }
                consumer.consume(exchange);
            });
    }
}
