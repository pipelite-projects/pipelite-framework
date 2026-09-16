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
package io.pipelite.components.http.undertow;

import io.pipelite.components.http.HttpChannelAdapter;
import io.pipelite.components.http.HttpConstants;
import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import io.undertow.server.BlockingHttpExchange;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefaultHttpHandler implements HttpHandler, ExchangeFactoryAware {

    private final HttpChannelAdapter component;

    private ExchangeFactory exchangeFactory;

    public DefaultHttpHandler(HttpChannelAdapter component) {
        this.component = component;
    }

    @Override
    public void handleRequest(HttpServerExchange httpServerExchange) throws IOException {

        try {
            final String resource = httpServerExchange.getRequestURI().replaceFirst("/", "");
            final Optional<Consumer> consumerHolder = component.tryResolveConsumer(resource);

            if (consumerHolder.isEmpty()) {
                httpServerExchange.setStatusCode(500);
                return;
            }

            final Consumer consumer = consumerHolder.get();
            // Per-resource, via HttpSourceConfigurer#method(...) - unset (null) means any method
            // is accepted, same as before this existed. Replaces the previous
            // ALLOWED_REQUEST_METHODS constant, which was declared but never actually checked
            // anywhere (issue #62): a single hardcoded [POST, PUT] list shared by every resource,
            // not a real per-endpoint restriction.
            final String allowedMethod = consumer.getEndpoint().getProperties().get(HttpConstants.METHOD_PROPERTY_NAME);
            if (allowedMethod != null && !httpServerExchange.getRequestMethod().toString().equalsIgnoreCase(allowedMethod)) {
                httpServerExchange.setStatusCode(405);
                return;
            }

            try (BlockingHttpExchange blockingHttpExchange = httpServerExchange.startBlocking()) {
                final InputStream requestBodyStream = blockingHttpExchange.getInputStream();
                String requestBodyAsText = new BufferedReader(
                    new InputStreamReader(requestBodyStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

                final Exchange exchange = exchangeFactory.createExchange(requestBodyAsText);
                consumer.consume(exchange);
                httpServerExchange.setStatusCode(201);
            }
        } finally {
            httpServerExchange.endExchange();
        }

    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

}
