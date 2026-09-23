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
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.undertow.Undertow;
import io.undertow.server.handlers.BlockingHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Issue #129: {@code DefaultHttpHandler.handleRequest(...)}'s own {@code
 * consumerHolder.isEmpty()} branch is never reached through {@code HttpChannelAdapter}'s real
 * wiring - {@code createHttpHandler()} wraps it in a {@code PathHandler}, whose own built-in
 * default handler (a plain 404) answers any unregistered path before {@code DefaultHttpHandler}
 * ever sees the request (confirmed empirically: a full-stack test hitting an unregistered
 * resource through the real adapter returns 404 whether this branch says 404 or 500 - see
 * {@code PipeliteHttpChannelAdapterConfigurationIntegrationTest}). This test exercises {@code
 * DefaultHttpHandler} directly, the only way that branch is actually reachable today, the same
 * way the original (unmerged) security finding on branch {@code
 * security/vulnerability-analysis-Q32026} demonstrated it.
 */
public class DefaultHttpHandlerTest {

    private Undertow server;
    private HttpClient httpClient;
    private int port;

    @Before
    public void setup() throws IOException {
        port = findFreePort();
        httpClient = HttpClient.newHttpClient();

        // No resource ever registered - every request reaches handleRequest() with
        // tryResolveConsumer(...) empty, since this bypasses HttpChannelAdapter's own PathHandler
        // wrapping entirely (see this class's own Javadoc).
        final HttpChannelAdapter adapter = new HttpChannelAdapter();
        final DefaultHttpHandler handler = new DefaultHttpHandler(adapter);
        handler.setExchangeFactory(Mockito.mock(ExchangeFactory.class));

        server = Undertow.builder()
            .addHttpListener(port, "127.0.0.1")
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

    @Test
    public void shouldReturn404NotAServerErrorForAnUnregisteredResource() throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/never-registered"))
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();

        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        Assert.assertEquals(404, response.statusCode());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

}
