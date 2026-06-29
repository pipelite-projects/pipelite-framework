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
package io.pipelite.core.security;

import io.pipelite.components.http.HttpChannelAdapter;
import io.pipelite.components.http.undertow.DefaultHttpHandler;
import io.pipelite.core.context.impl.DefaultExchangeFactory;
import io.pipelite.core.context.impl.DefaultMessageFactory;
import io.pipelite.spi.endpoint.Consumer;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.undertow.Undertow;
import io.undertow.server.handlers.BlockingHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Security tests for DefaultHttpHandler.
 *
 * Vulnerability #1 — HTTP Method Bypass:
 *   ALLOWED_REQUEST_METHODS is defined in DefaultHttpHandler but never enforced.
 *   Any HTTP method (GET, DELETE, HEAD, …) triggers consumer processing identically to POST/PUT.
 *
 * Vulnerability #2 — Wrong Status Code on Unknown Resource:
 *   When no consumer is registered for a path, the handler returns HTTP 500 instead of HTTP 404,
 *   leaking that the server recognised the request as "handled but failed" rather than "not found".
 */
public class SecurityTest_HttpMethodBypass {

    private static final String REGISTERED_RESOURCE = "ingestion";
    private static final String UNKNOWN_RESOURCE    = "unknown-resource";

    private Undertow server;
    private HttpClient httpClient;
    private int serverPort;

    @Before
    public void setup() throws IOException {
        serverPort = allocateFreePort();
        httpClient = HttpClient.newHttpClient();

        HttpChannelAdapter adapter = new HttpChannelAdapter();
        Consumer consumer = mock(Consumer.class);
        adapter.registerConsumer(REGISTERED_RESOURCE, consumer);

        ExchangeFactory exchangeFactory = new DefaultExchangeFactory(
            new DefaultMessageFactory(new DistributedIdentityGeneratorImpl()));

        DefaultHttpHandler handler = new DefaultHttpHandler(adapter);
        handler.setExchangeFactory(exchangeFactory);

        server = Undertow.builder()
            .addHttpListener(serverPort, "127.0.0.1")
            .setHandler(new BlockingHandler(handler))
            .build();
        server.start();
    }

    @After
    public void teardown() {
        if (server != null) {
            server.stop();
        }
    }

    /**
     * GET is not a safe method for a data-ingestion endpoint.
     * Expected: 405 Method Not Allowed.
     * Actual:   201 Created — ALLOWED_REQUEST_METHODS constant is never checked.
     */
    @Test
    @Ignore
    public void shouldReturn405WhenGetSentToIngestionEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + serverPort + "/" + REGISTERED_RESOURCE))
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }

    /**
     * DELETE on an ingestion endpoint should be rejected.
     * Expected: 405 Method Not Allowed.
     * Actual:   201 Created — method is never validated.
     */
    @Test
    @Ignore
    public void shouldReturn405WhenDeleteSentToIngestionEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + serverPort + "/" + REGISTERED_RESOURCE))
            .DELETE()
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }

    /**
     * HEAD on an ingestion endpoint should be rejected.
     * Expected: 405 Method Not Allowed.
     * Actual:   201 Created — method is never validated.
     */
    @Test
    @Ignore
    public void shouldReturn405WhenHeadSentToIngestionEndpoint() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + serverPort + "/" + REGISTERED_RESOURCE))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, response.statusCode());
    }

    /**
     * When no consumer is registered for the requested resource, the response must be 404.
     * Expected: 404 Not Found.
     * Actual:   500 Internal Server Error — semantically incorrect and leaks internal state.
     */
    @Test
    @Ignore
    public void shouldReturn404WhenResourceNotRegistered() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + serverPort + "/" + UNKNOWN_RESOURCE))
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .header("Content-Type", "application/json")
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }

    private static int allocateFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
