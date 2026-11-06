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
import io.pipelite.dsl.definition.SourceConcurrencyConfigurer;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Issue #171: which headers of an HTTP request reach the exchange is the flow's choice, made in one place for every
 * channel adapter, and none does by default. The tests go through the context and a real HTTP client.
 */
public class PipeliteHeaderPropagationIntegrationTest {

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

    /** What a step saw of one exchange. */
    private record Seen(String authorization, String idempotencyHeader, String tenant, String method, String path,
                        String query, String key, String body) {
    }

    private final List<Seen> seen = new CopyOnWriteArrayList<>();

    private FlowDefinition capturing(String name, String url, java.util.function.Consumer<HttpSourceConfigurer> configurer) {
        return Pipelite.defineFlow(name)
            .fromSource(url, configurer)
            .process("capture", (exchange, contribution) -> seen.add(new Seen(
                exchange.tryGetHeader("Authorization").orElse(null),
                exchange.tryGetHeader("Idempotency-Key").orElse(null),
                exchange.tryGetHeader("X-Tenant-Id").orElse(null),
                exchange.tryGetHeader("Http-Method").orElse(null),
                exchange.tryGetHeader("Http-Path").orElse(null),
                exchange.tryGetHeader("Http-Query-token").orElse(null),
                exchange.getIdempotencyKey(),
                exchange.getInputPayloadAs(String.class))))
            .build();
    }

    @Test
    public void givenNothingDeclared_thenNoHeaderOfTheRequestReachesTheExchange() throws Exception {
        start(capturing("orders", "http://orders", c -> { }));

        send("POST", "/orders?token=abc", "an order", "Authorization", "Bearer secret", "Idempotency-Key", "req-1");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> seen.size() == 1);
        final Seen only = seen.get(0);
        Assert.assertNull(only.authorization());
        Assert.assertNull(only.idempotencyHeader());
        Assert.assertNull(only.method());
        Assert.assertNull(only.path());
        Assert.assertNull(only.query());
        Assert.assertEquals("an order", only.body());
        Assert.assertNotEquals("the key is generated", "req-1", only.key());
    }

    @Test
    public void givenAFilter_thenOnlyTheNamedHeadersReachTheExchange_whateverTheCaseTheClientUsed() throws Exception {
        start(capturing("orders", "http://orders", c -> c.propagateHeaders(
            h -> h.filter("Idempotency-Key", "X-Tenant-Id", "Http-Method", "Http-Path", "Http-Query-token"))));

        send("POST", "/orders?token=abc", "an order",
            "Authorization", "Bearer secret", "idempotency-key", "req-1", "X-TENANT-ID", "t-1");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> seen.size() == 1);
        final Seen only = seen.get(0);
        Assert.assertNull("what the flow did not name stays out of the exchange, and of the files it is written to", only.authorization());
        Assert.assertEquals("req-1", only.idempotencyHeader());
        Assert.assertEquals("t-1", only.tenant());
        Assert.assertEquals("POST", only.method());
        Assert.assertEquals("/orders", only.path());
        Assert.assertEquals("abc", only.query());
    }

    @Test
    public void givenAll_thenEveryHeaderOfTheRequestReachesTheExchange() throws Exception {
        start(capturing("orders", "http://orders", c -> c.propagateHeaders(h -> h.all())));

        send("POST", "/orders", "an order", "Authorization", "Bearer secret", "X-Tenant-Id", "t-1");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> seen.size() == 1);
        final Seen only = seen.get(0);
        Assert.assertEquals("Bearer secret", only.authorization());
        Assert.assertEquals("t-1", only.tenant());
        Assert.assertEquals("POST", only.method());
    }

    @Test
    public void givenTheIdempotencyKeyIsDeclaredFromAHeader_thenARequestSentAgainIsTheSameWork() throws Exception {
        start(capturing("orders", "http://orders", c -> {
            c.propagateHeaders(h -> h.filter("Idempotency-Key"));
            c.idempotencyKey(k -> k.headerName("Idempotency-Key"));
        }));

        send("POST", "/orders", "an order", "Idempotency-Key", "req-1");
        send("POST", "/orders", "an order", "Idempotency-Key", "req-1");
        send("POST", "/orders", "another", "Idempotency-Key", "req-2");

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> seen.size() == 3);
        Assert.assertEquals(List.of("req-1", "req-1", "req-2"), seen.stream().map(Seen::key).toList());
    }

    @Test
    public void givenASinkWithTheCapability_thenOnlyTheNamedHeadersOfTheExchangeAreHandedOver() {
        HeadersSinkChannelAdapter.reset();
        start(Pipelite.defineFlow("internal")
            .fromSource("queue://internal")
            .process("headers", (exchange, contribution) -> {
                exchange.putHeader("Idempotency-Key", "req-1");
                exchange.putHeader("Authorization", "Bearer secret");
            })
            .toSink("headers-sink://out", (HeadersSinkChannelAdapter.HeadersSinkConfigurer c) ->
                c.propagateHeaders(h -> h.filter("idempotency-key")))
            .build());

        context.supplyExchange("queue://internal", context.createExchange("an order"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> HeadersSinkChannelAdapter.RECEIVED.size() == 1);
        Assert.assertEquals(Map.of("idempotency-key", "req-1"), HeadersSinkChannelAdapter.RECEIVED.get(0));
    }

    @Test
    public void givenASinkWithNoConfigurer_thenItIsRejectedAtStart() {
        context.registerFlowDefinition(Pipelite.defineFlow("internal")
            .fromSource("queue://internal")
            .process("noop", (exchange, contribution) -> { })
            .toSink("queue://other", (HeadersSinkChannelAdapter.HeadersSinkConfigurer c) -> c.propagateHeaders(h -> h.all()))
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("other")
            .fromSource("queue://other")
            .process("noop", (exchange, contribution) -> { })
            .build());

        try {
            context.start();
            Assert.fail("a queue has no settings for a sink: a callback is an error, and the headers cannot be declared on it");
        } catch (RuntimeException expected) {
            Assert.assertTrue(rootMessages(expected), rootMessages(expected).contains("does not support a SinkConfigurer"));
        }
    }

    @Test
    public void givenAConfigurerOfAnotherAdapter_thenItIsRejectedAtStart() {
        context.registerFlowDefinition(Pipelite.defineFlow("orders")
            .fromSource("http://orders", (SourceConcurrencyConfigurer c) -> { })
            .process("noop", (exchange, contribution) -> { })
            .build());

        try {
            context.start();
            Assert.fail("a configurer of a queue is not one of an http source");
        } catch (RuntimeException expected) {
            Assert.assertTrue(rootMessages(expected), rootMessages(expected).contains("Wrong SourceConfigurer type"));
        }
    }

    private static String rootMessages(Throwable throwable) {
        final StringBuilder messages = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            messages.append(current.getMessage()).append(" | ");
        }
        return messages.toString();
    }

    private void start(FlowDefinition... flows) {
        for (FlowDefinition flow : flows) {
            context.registerFlowDefinition(flow);
        }
        context.start();
    }

    private void send(String method, String path, String body, String... headers) throws IOException, InterruptedException {
        final HttpRequest.Builder request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + path))
            .method(method, HttpRequest.BodyPublishers.ofString(body));
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        final HttpResponse<String> response = HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
        Assert.assertEquals(201, response.statusCode());
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
