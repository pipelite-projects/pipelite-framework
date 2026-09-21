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

import io.pipelite.core.context.PipeliteContext;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;


/**
 * Issue #102: flows are linked only through URLs. The DSL methods that take a destination reject a
 * bare name when the flow is defined, and {@code PipeliteContext#supplyExchange} rejects it when
 * asked to deliver, so a value only known at runtime is covered too.
 */
public class PipeliteUrlOnlyLinkingTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String previousHome;
    private PipeliteContext pipeliteContext;

    @Before
    public void setup() throws Exception {
        previousHome = System.getProperty("pipelite.home");
        System.setProperty("pipelite.home", temporaryFolder.newFolder("home").toPath().toString());
        pipeliteContext = Pipelite.createContext();
    }

    @After
    public void tearDown() {
        pipeliteContext.stop();
        if (previousHome != null) {
            System.setProperty("pipelite.home", previousHome);
        } else {
            System.clearProperty("pipelite.home");
        }
    }

    /**
     * Issue #112: a bare sink used to be a producer that delivered nowhere, the same as no sink.
     */
    @Test
    public void givenABareSink_whenToSinkIsCalled_thenRejectedAtDefinitionSayingWhatToWriteOrToLeaveItOut() {
        try {
            Pipelite.defineFlow("bare-sink-flow")
                .fromSource("queue://bare-sink-in")
                .toSink("kitchen-start")
                .build();
            Assert.fail("expected the bare sink to be rejected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("toSink(\"kitchen-start\")"));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("Write the URL of a channel adapter"));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("'queue://kitchen-start'"));
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("leave toSink(...) out"));
        }
    }

    @Test
    public void givenAPlaceholderThatResolvesToABareSink_whenTheContextStarts_thenItFailsNamingTheSink() {
        final PipeliteContext withPlaceholders = Pipelite.createContext(rawUrl -> rawUrl.replace("${sink}", "orders-out"));
        try {
            withPlaceholders.registerFlowDefinition(Pipelite.defineFlow("placeholder-sink-flow")
                .fromSource("queue://placeholder-sink-in")
                .toSink("${sink}")
                .build());
            withPlaceholders.start();
            Assert.fail("expected the context not to start");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("toSink(\"orders-out\"): 'orders-out' is not a URL"));
        } finally {
            withPlaceholders.stop();
        }
    }

    @Test
    public void givenAFlowWithNoSink_whenAnExchangeIsSupplied_thenItEndsAfterItsLastStep() {
        final java.util.List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("no-sink-flow")
            .fromSource("queue://no-sink-in")
            .process("record", (exchange, contribution) -> seen.add(exchange.getInputPayloadAs(String.class)))
            .build());
        pipeliteContext.start();

        pipeliteContext.supplyExchange("queue://no-sink-in", pipeliteContext.getExchangeFactory().createExchange("done-1"));

        org.awaitility.Awaitility.await().atMost(10, java.util.concurrent.TimeUnit.SECONDS).until(() -> seen.size() == 1);
        Assert.assertEquals(java.util.List.of("done-1"), seen);
    }

    @Test
    public void givenABareRouteDestination_whenThenIsCalled_thenRejectedAtDefinition() {
        try {
            Pipelite.defineFlow("bare-then-flow")
                .fromSource("queue://bare-then-in")
                .toRoute(routes -> routes.dynamic()
                    .when("Headers['x'] == 'y'").then("premium-flow")
                    .otherwise("queue://default-flow")
                    .end())
                .build();
            Assert.fail("expected the bare route destination to be rejected");
        } catch (IllegalArgumentException expected) {
            assertRejectionSays(expected, "then(...)", "premium-flow");
        }
    }

    @Test
    public void givenABareDefaultRoute_whenOtherwiseIsCalled_thenRejectedAtDefinition() {
        try {
            Pipelite.defineFlow("bare-otherwise-flow")
                .fromSource("queue://bare-otherwise-in")
                .toRoute(routes -> routes.dynamic()
                    .when("Headers['x'] == 'y'").then("queue://premium-flow")
                    .otherwise("default-flow")
                    .end())
                .build();
            Assert.fail("expected the bare default route to be rejected");
        } catch (IllegalArgumentException expected) {
            assertRejectionSays(expected, "otherwise(...)", "default-flow");
        }
    }

    @Test
    public void givenABareRecipient_whenToRecipientsIsCalled_thenRejectedAtDefinition() {
        try {
            Pipelite.defineFlow("bare-recipient-flow")
                .fromSource("queue://bare-recipient-in")
                .toRecipientList(recipients -> recipients
                    .toRecipients("queue://destination-01-start", "destination-02-start")
                    .when("Headers['x'] eq 'y'")
                        .toRecipient("queue://destination-03-start")
                    .end())
                .build();
            Assert.fail("expected the bare recipient to be rejected");
        } catch (IllegalArgumentException expected) {
            assertRejectionSays(expected, "toRecipients(...)", "destination-02-start");
        }
    }

    @Test
    public void givenABareWireTapTarget_whenWireTapIsCalled_thenRejectedAtDefinition() {
        try {
            Pipelite.defineFlow("bare-wire-tap-flow")
                .fromSource("queue://bare-wire-tap-in")
                .wireTap("audit", "audit-flow");
            Assert.fail("expected the bare wire tap target to be rejected");
        } catch (IllegalArgumentException expected) {
            assertRejectionSays(expected, "wireTap(...)", "audit-flow");
        }
    }

    @Test
    public void givenARouteDestinationBuiltFromAnExpression_thenItIsNotCheckedAtDefinition() {
        // Only known when an exchange is routed: it is checked then, by supplyExchange.
        Pipelite.defineFlow("dynamic-route-flow")
            .fromSource("queue://dynamic-route-in")
            .toRoute(routes -> routes.dynamic()
                .when("Headers['x'] == 'y'").then("#{Headers['destination']}")
                .otherwise("queue://default-flow")
                .end())
            .build();
    }

    @Test
    public void givenABareName_whenSupplyExchangeIsCalled_thenRejectedSayingWhatToWrite() {
        final ExchangeImpl exchange = pipeliteContext.getExchangeFactory().createExchange("payload");
        try {
            pipeliteContext.supplyExchange("kitchen-start", exchange);
            Assert.fail("expected the bare name to be rejected");
        } catch (IllegalArgumentException expected) {
            final String message = expected.getMessage();
            Assert.assertTrue(message, message.contains("'kitchen-start' is not a URL"));
            Assert.assertTrue(message, message.contains("queue://kitchen-start"));
        }
    }

    /**
     * Issue #100, brought in by #102: {@code queue://} is the only way to an internal flow, and an
     * orphan one used to be discarded without a trace (a bare orphan, which failed, was the other way in).
     */
    @Test
    public void givenAQueueURLNoFlowReads_whenSupplyExchangeIsCalled_thenItFailsInsteadOfDiscarding() {
        pipeliteContext.start();
        final ExchangeImpl exchange = pipeliteContext.getExchangeFactory().createExchange("payload");
        try {
            pipeliteContext.supplyExchange("queue://nobody-declares-this", exchange);
            Assert.fail("expected the orphan queue:// target to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("nobody-declares-this"));
        }
    }

    private static void assertRejectionSays(IllegalArgumentException exception, String construct, String bareName) {
        final String message = exception.getMessage();
        Assert.assertTrue(message, message.contains(construct));
        Assert.assertTrue(message, message.contains("'" + bareName + "' is not a URL"));
        Assert.assertTrue(message, message.contains("queue://" + bareName));
    }

}
