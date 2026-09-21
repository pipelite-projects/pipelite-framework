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
package io.pipelite.core.flow.route;

import io.pipelite.core.Pipelite;
import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.dsl.ChannelProtocols;
import io.pipelite.dsl.route.RoutingSlip;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Issue #86: a {@code RoutingSlip} set with {@code ExchangeImpl#setRoutingSlip(...)} (the {@code Exchange} setter is disabled, see there) is followed at
 * the end of each flow it goes through, taking priority over the flow's own exit (sink, toRoute,
 * return address), which only runs once the slip is exhausted.
 */
public class RoutingSlipRouterNodeTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String previousHome;
    private DefaultPipeliteContext context;

    /** Names of the flows visited, in order - each flow's own last step appends its name. */
    private final List<String> visits = new CopyOnWriteArrayList<>();

    @Before
    public void setup() throws Exception {
        // Isolated pipelite.home: the default durable inbox/dump repository are file-backed.
        previousHome = System.getProperty("pipelite.home");
        System.setProperty("pipelite.home", temporaryFolder.newFolder("home").toPath().toString());
        context = new DefaultPipeliteContext();
    }

    @After
    public void tearDown() {
        context.stop();
        if (previousHome != null) {
            System.setProperty("pipelite.home", previousHome);
        } else {
            System.clearProperty("pipelite.home");
        }
    }

    private void visit(String name) {
        visits.add(name);
    }

    private void supply(String source, ExchangeImpl exchange) {
        context.supplyExchange(ChannelProtocols.queueURL(source), exchange);
    }

    @Test
    public void givenASlipOfTwoRoutes_thenTheExchangeVisitsThemInOrder() {

        context.registerFlowDefinition(Pipelite.defineFlow("a-flow")
            .fromSource("queue://a-start")
            .process("plan", (io, c) -> {
                visit("a");
                ((ExchangeImpl) io).setRoutingSlip(RoutingSlip.create("queue://b-start", "queue://c-start"));
            })
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("b-flow")
            .fromSource("queue://b-start")
            .process("step", (io, c) -> visit("b"))
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("c-flow")
            .fromSource("queue://c-start")
            .process("step", (io, c) -> visit("c"))
            .build());
        context.start();

        supply("a-start", context.getExchangeFactory().createExchange("payload"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> visits.size() == 3);
        Assert.assertEquals(List.of("a", "b", "c"), visits);
    }

    @Test
    public void givenARouteLeft_thenTheFlowsSinkDoesNotRunButItRunsOnceTheSlipIsExhausted() {

        final List<String> sinks = new CopyOnWriteArrayList<>();
        context.registerFlowDefinition(Pipelite.defineFlow("sink-a")
            .fromSource("queue://sink-a-start")
            .process("plan", (io, c) -> {
                visit("a");
                ((ExchangeImpl) io).setRoutingSlip(RoutingSlip.create("queue://sink-b-start", "queue://sink-c-start"));
            })
            .toSink("queue://sink-a-out")
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("sink-b")
            .fromSource("queue://sink-b-start")
            .process("step", (io, c) -> visit("b"))
            .toSink("queue://sink-b-out")
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("sink-c")
            .fromSource("queue://sink-c-start")
            .process("step", (io, c) -> visit("c"))
            .toSink("queue://sink-c-out")
            .build());
        for (String name : List.of("a", "b", "c")) {
            context.registerFlowDefinition(Pipelite.defineFlow("sink-" + name + "-target")
                .fromSource("queue://sink-" + name + "-out")
                .process("record", (io, c) -> sinks.add(name))
                .build());
        }
        context.start();

        supply("sink-a-start", context.getExchangeFactory().createExchange("payload"));

        // Only the last flow of the itinerary exits through its sink.
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> sinks.size() == 1);
        Assert.assertEquals(List.of("a", "b", "c"), visits);
        Assert.assertEquals(List.of("c"), sinks);
    }

    @Test
    public void givenAFlowEndingWithToRoute_thenTheSlipWinsWhileItHasRoutesAndTheRouteRunsWhenExhausted() {

        final List<String> routed = new CopyOnWriteArrayList<>();
        context.registerFlowDefinition(Pipelite.defineFlow("route-a")
            .fromSource("queue://route-a-start")
            .process("plan", (io, c) -> {
                visit("a");
                ((ExchangeImpl) io).setRoutingSlip(RoutingSlip.create("queue://route-b-start", "queue://route-c-start"));
            })
            .build());
        for (String name : List.of("b", "c")) {
            context.registerFlowDefinition(Pipelite.defineFlow("route-" + name)
                .fromSource("queue://route-" + name + "-start")
                .process("step", (io, c) -> visit(name))
                .toRoute(routes -> routes.dynamic()
                    .when("#inputPayload == 'payload'").then("queue://route-" + name + "-target")
                    .otherwise("queue://route-" + name + "-target")
                    .end())
                .build());
            context.registerFlowDefinition(Pipelite.defineFlow("route-" + name + "-target-flow")
                .fromSource("queue://route-" + name + "-target")
                .process("record", (io, c) -> routed.add(name))
                .build());
        }
        context.start();

        supply("route-a-start", context.getExchangeFactory().createExchange("payload"));

        // b is an intermediate hop: its toRoute is skipped. c is the last: its toRoute runs.
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> routed.size() == 1);
        Assert.assertEquals(List.of("a", "b", "c"), visits);
        Assert.assertEquals(List.of("c"), routed);
    }

    @Test
    public void givenASlipAndAReturnAddress_thenTheReplyHappensOnlyOnceTheSlipIsExhausted() {

        final List<String> replies = new CopyOnWriteArrayList<>();
        context.registerFlowDefinition(Pipelite.defineFlow("reply-a")
            .fromSource("queue://reply-a-start")
            .process("plan", (io, c) -> {
                visit("a");
                ((ExchangeImpl) io).setRoutingSlip(RoutingSlip.create("queue://reply-b-start", "queue://reply-c-start"));
            })
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("reply-b")
            .fromSource("queue://reply-b-start")
            .process("step", (io, c) -> visit("b"))
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("reply-c")
            .fromSource("queue://reply-c-start")
            .process("step", (io, c) -> visit("c"))
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("reply-origin")
            .fromSource("queue://reply-origin-start")
            .process("record", (io, c) -> replies.add("origin"))
            .build());
        context.start();

        final ExchangeImpl exchange = context.getExchangeFactory().createExchange("payload");
        exchange.setReturnAddress("queue://reply-origin-start");
        supply("reply-a-start", exchange);

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> replies.size() == 1);
        // Never a second, parallel delivery of the same exchange to the return address.
        Assert.assertEquals(List.of("a", "b", "c"), visits);
        Assert.assertEquals(1, replies.size());
    }

    @Test
    public void givenAStepThatStopsTheExecution_thenTheSlipIsNotFollowed() throws Exception {

        context.registerFlowDefinition(Pipelite.defineFlow("stop-a")
            .fromSource("queue://stop-a-start")
            .process("plan", (io, c) -> {
                ((ExchangeImpl) io).setRoutingSlip(RoutingSlip.create("queue://stop-b-start"));
                c.stopExecution();
            })
            .build());
        context.registerFlowDefinition(Pipelite.defineFlow("stop-b")
            .fromSource("queue://stop-b-start")
            .process("step", (io, c) -> visit("b"))
            .build());
        context.start();

        supply("stop-a-start", context.getExchangeFactory().createExchange("payload"));

        Thread.sleep(1500);
        Assert.assertTrue("a filtered exchange must not hop", visits.isEmpty());
    }

    @Test
    public void givenARouteThatMatchesNoFlow_thenTheFailureIsReportedNamingTheRoute() {

        final AtomicReference<Throwable> failure = new AtomicReference<>();
        context.registerFlowDefinition(Pipelite.defineFlow("missing-a")
            .fromSource("queue://missing-a-start")
            .process("plan", (io, c) -> ((ExchangeImpl) io).setRoutingSlip(RoutingSlip.create("queue://nobody-declares-this")))
            .withExceptionHandler((exception, ioContext) -> failure.set(exception))
            .build());
        context.start();

        supply("missing-a-start", context.getExchangeFactory().createExchange("payload"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> failure.get() != null);
        Assert.assertTrue(failure.get().getMessage().contains("nobody-declares-this"));
        Assert.assertTrue(failure.get().getMessage().contains("missing-a"));
    }

}
