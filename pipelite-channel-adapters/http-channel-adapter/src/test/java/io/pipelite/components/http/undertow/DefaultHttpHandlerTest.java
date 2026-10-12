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
import io.pipelite.components.http.HttpEndpoint;
import io.pipelite.dsl.Exchange;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import io.pipelite.spi.source.EventSource;
import io.pipelite.spi.source.SourceContext;
import io.undertow.Undertow;
import io.undertow.server.handlers.BlockingHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;

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
    private HttpChannelAdapter adapter;
    private HttpClient httpClient;
    private int port;

    @Before
    public void setup() throws IOException {
        port = findFreePort();
        httpClient = HttpClient.newHttpClient();

        // No resource ever registered - every request reaches handleRequest() with
        // tryResolveConsumer(...) empty, since this bypasses HttpChannelAdapter's own PathHandler
        // wrapping entirely (see this class's own Javadoc).
        adapter = new HttpChannelAdapter();
        final DefaultHttpHandler handler = new DefaultHttpHandler(adapter);
        

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

    /**
     * Issue #159: a request whose exchange cannot be accepted (the durable inbox cannot be written) is not
     * answered as if it had been. Before, the failure was swallowed by the consumer and handled by the flow's
     * exception handler, and the client got a 201 for a request that was lost.
     */
    @Test
    public void shouldAnswer500WhenTheRequestCannotBeAccepted() throws IOException, InterruptedException {
        startSource("rejecting", new RecordingContext(true));

        final HttpResponse<String> response = post("/rejecting", "payload");

        Assert.assertEquals(500, response.statusCode());
    }

    @Test
    public void shouldAnswer201AndHandTheBodyToTheFlowWhenTheRequestIsAccepted() throws IOException, InterruptedException {
        final RecordingContext context = new RecordingContext(false);
        startSource("accepting", context);

        final HttpResponse<String> response = post("/accepting", "the body");

        Assert.assertEquals(201, response.statusCode());
        Assert.assertEquals(1, context.accepted.size());
        Assert.assertEquals("the body", context.accepted.get(0).getInputPayload());
    }

    private void startSource(String resource, RecordingContext context) {
        final EventSource source = (EventSource) new HttpEndpoint(EndpointURL.parse(resource), adapter).createSource();
        source.start(context);
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + path))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** What the framework gives a source, reduced to recording what is handed over, or refusing it. */
    private static final class RecordingContext implements SourceContext {
        private final boolean refuse;
        private final List<Exchange> accepted = new ArrayList<>();

        RecordingContext(boolean refuse) {
            this.refuse = refuse;
        }

        @Override
        public String flowName() {
            return "http-test-flow";
        }

        @Override
        public Exchange newExchange(Object payload) {
            final ExchangeImpl exchange = new ExchangeImpl(new SimpleMessage(UUID.randomUUID().toString()));
            exchange.setInputPayload(payload);
            return exchange;
        }

        @Override
        public void accept(Exchange exchange) {
            if (refuse) {
                throw new IllegalStateException("the exchange cannot be durably accepted");
            }
            accepted.add(exchange);
        }

        @Override
        public ThreadFactory threadFactory() {
            return Thread::new;
        }

        @Override
        public boolean isRunning() {
            return true;
        }
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

}
