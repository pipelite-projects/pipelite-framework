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
import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.flow.exchange.Exchange;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import io.undertow.server.BlockingHttpExchange;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefaultHttpHandler implements HttpHandler, ExchangeFactoryAware {

    private static final HttpString REQUEST_METHOD_POST = new HttpString("POST");
    private static final HttpString REQUEST_METHOD_PUT = new HttpString("PUT");

    private static final Collection<HttpString> ALLOWED_REQUEST_METHODS = Arrays.asList(REQUEST_METHOD_PUT, REQUEST_METHOD_POST);

    private final HttpChannelAdapter component;

    private ExchangeFactory exchangeFactory;

    public DefaultHttpHandler(HttpChannelAdapter component) {
        this.component = component;
    }

    @Override
    public void handleRequest(HttpServerExchange httpServerExchange) throws IOException {

        try(BlockingHttpExchange blockingHttpExchange = httpServerExchange.startBlocking()){
            final InputStream requestBodyStream = blockingHttpExchange.getInputStream();
            String requestBodyAsText = new BufferedReader(
                new InputStreamReader(requestBodyStream, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

            final String resource = httpServerExchange.getRequestURI().replaceFirst("/", "");
            final Optional<Consumer> consumerHolder = component.tryResolveConsumer(resource);

            if(consumerHolder.isPresent()){
                final Exchange exchange = exchangeFactory.createExchange(requestBodyAsText);
                final Consumer consumer = consumerHolder.get();
                consumer.consume(exchange);
                httpServerExchange.setStatusCode(201);
            }else {
                httpServerExchange.setStatusCode(500);
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
