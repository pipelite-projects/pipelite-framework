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
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeFactoryAware;
import io.undertow.server.BlockingHttpExchange;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.RequestTooBigException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
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

            // Rejected before reading a single byte (issue #52) whenever the client sends a
            // truthful Content-Length - getMaxEntitySize() reflects whatever
            // HttpChannelConfiguration#setMaxEntitySize configured, via
            // UndertowOptions.MAX_ENTITY_SIZE. The mid-read catches below are the fallback for a
            // chunked request (no Content-Length to check upfront) or one that lies about it.
            final long maxEntitySize = httpServerExchange.getMaxEntitySize();
            final long contentLength = httpServerExchange.getRequestContentLength();
            if (maxEntitySize > 0 && contentLength > maxEntitySize) {
                httpServerExchange.setStatusCode(413);
                return;
            }

            try (BlockingHttpExchange blockingHttpExchange = httpServerExchange.startBlocking()) {
                final InputStream requestBodyStream = blockingHttpExchange.getInputStream();
                String requestBodyAsText = new BufferedReader(
                    new InputStreamReader(requestBodyStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));

                final ExchangeImpl exchange = exchangeFactory.createExchange(requestBodyAsText);
                consumer.consume(exchange);
                httpServerExchange.setStatusCode(201);
            } catch (RequestTooBigException tooBig) {
                // Undertow itself enforces UndertowOptions.MAX_ENTITY_SIZE by throwing this
                // mid-read for a chunked (Content-Length-less) request that turns out too big.
                rejectAsTooLargeIfStillPossible(httpServerExchange);
            } catch (UncheckedIOException possiblyTooBig) {
                // BufferedReader#lines() (used above) wraps every IOException, including
                // RequestTooBigException, thrown while iterating its Stream - the same condition as
                // the direct catch above, just reached through Stream.collect(...) instead of a
                // plain read() call.
                if (!(possiblyTooBig.getCause() instanceof RequestTooBigException)) {
                    throw possiblyTooBig;
                }
                rejectAsTooLargeIfStillPossible(httpServerExchange);
            }
        } finally {
            httpServerExchange.endExchange();
        }

    }

    @Override
    public void setExchangeFactory(ExchangeFactory exchangeFactory) {
        this.exchangeFactory = exchangeFactory;
    }

    /**
     * Undertow's own reaction to exceeding {@code MAX_ENTITY_SIZE} mid-read is to terminate the
     * connection outright (its {@link RequestTooBigException}'s own message: "Connection
     * terminated") - by the time that reaches this handler, the exchange may already be past the
     * point where a status code can be set at all. Best-effort: a clean 413 when still possible,
     * a reset connection (no worse than before this existed) otherwise.
     */
    private static void rejectAsTooLargeIfStillPossible(HttpServerExchange httpServerExchange) {
        if (!httpServerExchange.isResponseStarted()) {
            httpServerExchange.setStatusCode(413);
        }
    }

}
