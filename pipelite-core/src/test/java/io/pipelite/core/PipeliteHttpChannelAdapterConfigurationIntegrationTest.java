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
package io.pipelite.core;

import io.pipelite.components.http.config.HttpChannelConfigurer;
import io.pipelite.core.context.ConfigurablePipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Issues #48/#52: {@code HttpChannelAdapter}'s listening port and request body size cap are now
 * configurable adapter-wide via {@code context.addChannelConfigurer((HttpChannelConfigurer) ...)}
 * - the same mechanism {@code KafkaChannelAdapter} already uses for {@code bootstrapServers}.
 * Before this, the port was hardcoded to 80 and a request body was read into memory with no size
 * bound at all.
 */
public class PipeliteHttpChannelAdapterConfigurationIntegrationTest {

    private ConfigurablePipeliteContext context;

    @After
    public void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    public void givenACustomPortIsConfigured_thenTheServerListensOnItInsteadOfTheOldHardcodedOne() throws Exception {

        final int customPort = findFreePort();
        final AtomicReference<String> received = new AtomicReference<>();

        final FlowDefinition flow = Pipelite.defineFlow("http-custom-port")
            .fromSource("http://ingress")
            .process("capture", (exchange, contribution) -> received.set(exchange.getInputPayloadAs(String.class)))
            .build();

        context = (ConfigurablePipeliteContext) Pipelite.createContext();
        context.addChannelConfigurer((HttpChannelConfigurer) configuration -> configuration.setPort(customPort));
        context.registerFlowDefinition(flow);
        context.start();

        final HttpResponse<String> response = post(customPort, "/ingress", "hello");

        Assert.assertEquals(201, response.statusCode());
        Assert.assertEquals("hello", received.get());
    }

    /**
     * Issue #129: through the real adapter wiring, an unregistered path never even reaches
     * {@code DefaultHttpHandler} - {@code HttpChannelAdapter#createHttpHandler()} wraps it in a
     * {@code PathHandler}, whose own built-in default handler answers with 404 before dispatching
     * anywhere. See {@code DefaultHttpHandlerTest} (http-channel-adapter module) for a test that
     * actually exercises {@code DefaultHttpHandler}'s own {@code consumerHolder.isEmpty()} branch
     * directly - the only way it's reachable.
     */
    @Test
    public void givenAnUnregisteredResourceIsRequested_thenTheResponseIs404NotAServerError() throws Exception {

        final int port = findFreePort();

        final FlowDefinition flow = Pipelite.defineFlow("http-unregistered-resource")
            .fromSource("http://ingress")
            .process("noop", (exchange, contribution) -> { })
            .build();

        context = (ConfigurablePipeliteContext) Pipelite.createContext();
        context.addChannelConfigurer((HttpChannelConfigurer) configuration -> configuration.setPort(port));
        context.registerFlowDefinition(flow);
        context.start();

        final HttpResponse<String> response = post(port, "/never-registered", "hello");

        Assert.assertEquals(404, response.statusCode());
    }

    @Test
    public void givenAMaxEntitySizeIsConfigured_thenAnOversizedRequestIsRejectedNotAccepted() throws Exception {

        final int port = findFreePort();
        final AtomicReference<String> received = new AtomicReference<>();

        final FlowDefinition flow = Pipelite.defineFlow("http-max-entity-size")
            .fromSource("http://ingress")
            .process("capture", (exchange, contribution) -> received.set(exchange.getInputPayloadAs(String.class)))
            .build();

        context = (ConfigurablePipeliteContext) Pipelite.createContext();
        context.addChannelConfigurer((HttpChannelConfigurer) configuration -> {
            configuration.setPort(port);
            configuration.setMaxEntitySize(8);
        });
        context.registerFlowDefinition(flow);
        context.start();

        final HttpResponse<String> response = post(port, "/ingress", "this body is well over 8 bytes");

        Assert.assertEquals(413, response.statusCode());
        Assert.assertNull("an oversized request must never reach the flow", received.get());
    }

    private static HttpResponse<String> post(int port, String path, String body) throws IOException, InterruptedException {
        final HttpClient client = HttpClient.newHttpClient();
        final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

}
