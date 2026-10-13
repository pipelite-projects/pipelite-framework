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

import io.pipelite.components.http.HttpSourceConfigurer;
import io.pipelite.components.http.config.HttpChannelConfigurer;
import io.pipelite.core.context.ConfigurablePipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import org.awaitility.Awaitility;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Characterization of what an {@code http://} source does with a request, written before the adapter
 * contract changes (issue #159): the tests go through the context and the HTTP client only, so they
 * keep their meaning when the adapter is rewritten against the new SPI. What they pin down:
 * a request is <em>accepted</em> (201) once it is queued, not once the flow has processed it; the body
 * reaches the flow as text with its lines joined by {@code \n}; an allowed method is enforced before
 * anything is queued; each resource reaches only the flow that declared it.
 */
public class PipeliteHttpSourceIntegrationTest {

    private int port;
    private ConfigurablePipeliteContext context;

    @Before
    public void setup() throws IOException {
        port = findFreePort();
        context = (ConfigurablePipeliteContext) Pipelite.createContext();
        context.addChannelConfigurer((HttpChannelConfigurer) configuration -> configuration.setPort(port));
    }

    @After
    public void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    public void givenALineBreakInTheBody_thenTheFlowReceivesTheLinesJoinedByNewlineWithoutTheTrailingOne() throws Exception {
        final AtomicReference<String> received = new AtomicReference<>();
        start(Pipelite.defineFlow("http-multi-line")
            .fromSource("http://ingress")
            .process("capture", (exchange, contribution) -> received.set(exchange.getInputPayloadAs(String.class)))
            .build());

        final HttpResponse<String> response = send(port, "POST", "/ingress", "first\nsecond\n");

        Assert.assertEquals(201, response.statusCode());
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> received.get() != null);
        Assert.assertEquals("first\nsecond", received.get());
    }

    @Test
    public void givenTheFlowFails_thenTheRequestIsStillAnsweredWith201BecauseItWasOnlyAccepted() throws Exception {
        final AtomicInteger attempts = new AtomicInteger();
        start(Pipelite.defineFlow("http-failing-flow")
            .fromSource("http://ingress")
            .process("fail", (exchange, contribution) -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("the flow fails after the request was accepted");
            })
            .build());

        final HttpResponse<String> response = send(port, "POST", "/ingress", "payload");

        Assert.assertEquals(201, response.statusCode());
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> attempts.get() == 1);
    }

    @Test
    public void givenAMethodIsAllowedOnTheSource_whenAnotherMethodIsUsed_thenItIsRejectedWith405AndNothingIsProcessed() throws Exception {
        final AtomicInteger processed = new AtomicInteger();
        start(Pipelite.defineFlow("http-allowed-method")
            .fromSource("http://post-only", (HttpSourceConfigurer c) -> c.method("POST"))
            .process("count", (exchange, contribution) -> processed.incrementAndGet())
            .build());

        final HttpResponse<String> rejected = send(port, "PUT", "/post-only", "payload");
        Assert.assertEquals(405, rejected.statusCode());

        final HttpResponse<String> accepted = send(port, "POST", "/post-only", "payload");
        Assert.assertEquals(201, accepted.statusCode());

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processed.get() == 1);
        Assert.assertEquals("only the POST may have reached the flow", 1, processed.get());
    }

    @Test
    public void givenTwoFlowsOnDifferentResources_thenEachReceivesOnlyTheRequestsOfItsOwnResource() throws Exception {
        final AtomicReference<String> orders = new AtomicReference<>();
        final AtomicReference<String> invoices = new AtomicReference<>();
        start(
            Pipelite.defineFlow("http-orders")
                .fromSource("http://orders")
                .process("capture", (exchange, contribution) -> orders.set(exchange.getInputPayloadAs(String.class)))
                .build(),
            Pipelite.defineFlow("http-invoices")
                .fromSource("http://invoices")
                .process("capture", (exchange, contribution) -> invoices.set(exchange.getInputPayloadAs(String.class)))
                .build());

        Assert.assertEquals(201, send(port, "POST", "/orders", "an order").statusCode());
        Assert.assertEquals(201, send(port, "POST", "/invoices", "an invoice").statusCode());

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> orders.get() != null && invoices.get() != null);
        Assert.assertEquals("an order", orders.get());
        Assert.assertEquals("an invoice", invoices.get());
    }

    private void start(FlowDefinition... flows) {
        for (FlowDefinition flow : flows) {
            context.registerFlowDefinition(flow);
        }
        context.start();
    }

    private static HttpResponse<String> send(int port, String method, String path, String body) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .method(method, HttpRequest.BodyPublishers.ofString(body))
            .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
