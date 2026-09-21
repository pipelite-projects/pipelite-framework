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
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    @Test
    public void givenABareRouteDestination_whenThenIsCalled_thenRejectedAtDefinition() {
        try {
            Pipelite.defineFlow("bare-then-flow")
                .fromSource("bare-then-in")
                .toRoute(routes -> routes.dynamic()
                    .when("Headers['x'] == 'y'").then("premium-flow")
                    .otherwise("link://default-flow")
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
                .fromSource("bare-otherwise-in")
                .toRoute(routes -> routes.dynamic()
                    .when("Headers['x'] == 'y'").then("link://premium-flow")
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
                .fromSource("bare-recipient-in")
                .toRecipientList(recipients -> recipients
                    .toRecipients("link://destination-01-start", "destination-02-start")
                    .when("Headers['x'] eq 'y'")
                        .toRecipient("link://destination-03-start")
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
                .fromSource("bare-wire-tap-in")
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
            .fromSource("dynamic-route-in")
            .toRoute(routes -> routes.dynamic()
                .when("Headers['x'] == 'y'").then("#{Headers['destination']}")
                .otherwise("link://default-flow")
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
            Assert.assertTrue(message, message.contains("link://kitchen-start"));
        }
    }

    /**
     * Issue #100, brought in by #102: {@code link://} is the only way to an internal flow, and an
     * orphan one used to be discarded without a trace (a bare orphan, which failed, was the other way in).
     */
    @Test
    public void givenALinkURLNoFlowDeclares_whenSupplyExchangeIsCalled_thenItFailsInsteadOfDiscarding() {
        pipeliteContext.start();
        final ExchangeImpl exchange = pipeliteContext.getExchangeFactory().createExchange("payload");
        try {
            pipeliteContext.supplyExchange("link://nobody-declares-this", exchange);
            Assert.fail("expected the orphan link:// target to fail");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("nobody-declares-this"));
        }
    }

    @Test
    public void givenALinkSinkNoFlowDeclares_thenTheFailureIsReportedToTheFlowsExceptionHandler() {
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("orphan-sink-flow")
            .fromSource("orphan-sink-in")
            .toSink("link://nobody-declares-this-either")
            .withExceptionHandler((exception, exchange) -> failure.set(exception))
            .build());
        pipeliteContext.start();

        pipeliteContext.supplyExchange("link://orphan-sink-in", pipeliteContext.getExchangeFactory().createExchange("payload"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> failure.get() != null);
        Assert.assertTrue(failure.get().getMessage(), failure.get().getMessage().contains("nobody-declares-this-either"));
    }

    private static void assertRejectionSays(IllegalArgumentException exception, String construct, String bareName) {
        final String message = exception.getMessage();
        Assert.assertTrue(message, message.contains(construct));
        Assert.assertTrue(message, message.contains("'" + bareName + "' is not a URL"));
        Assert.assertTrue(message, message.contains("link://" + bareName));
    }

}
