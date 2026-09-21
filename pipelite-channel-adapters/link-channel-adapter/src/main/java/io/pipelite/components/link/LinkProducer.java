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

import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.endpoint.DefaultProducer;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LinkProducer extends DefaultProducer {

    private final Logger sysLogger = LoggerFactory.getLogger(getClass());

    public LinkProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void doProcess(ExchangeImpl exchange) {

        final LinkChannelAdapter component = endpoint.getChannelAdapter(LinkChannelAdapter.class);
        final EndpointURL endpointURL = endpoint.getEndpointURL();

        // Fails instead of dropping the exchange when no flow declares fromSource(resource): it used
        // to be discarded in silence (issue #100), which only mattered less while a bare
        // destination, which did fail, was another way to reach an internal flow (issue #102).
        // The failure goes through the flow's exception handler like any failed producer (#92).
        final Consumer consumer = component.tryResolveConsumer(endpointURL.getResource())
            .orElseThrow(() -> new IllegalArgumentException(String.format(
                "Unrecognized destination 'link://%s', unable to supply exchange - " +
                    "no registered flow declares fromSource('%s')", endpointURL.getResource(), endpointURL.getResource())));

        if(sysLogger.isDebugEnabled()){
            sysLogger.debug("Redirecting exchange to '{}'", endpointURL.getResource());
        }
        consumer.consume(exchange);
    }
}
