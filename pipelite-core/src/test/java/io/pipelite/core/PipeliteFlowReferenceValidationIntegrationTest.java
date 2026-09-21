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

import io.pipelite.core.context.impl.DefaultPipeliteContext;
import io.pipelite.core.context.ContextValidationException;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.internal.validation.ContextValidator;
import io.pipelite.dsl.definition.FlowDefinition;
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

/**
 * Issue #88: {@code PipeliteContext#start()} validates that the flows fit together before it starts
 * anything, and fails once with every problem instead of losing exchanges at runtime.
 */
public class PipeliteFlowReferenceValidationIntegrationTest {

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
    public void givenAQueueNoFlowReads_whenTheContextStarts_thenItFailsListingEveryProblemAndStartsNothing() {

        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("order-ingress-flow")
            .fromSource("http://orders")
            .wireTap("audit", "queue://missing-audit")
            .toSink("queue://kicthen-start")   // typo: the real source is "kitchen-start"
            .build());
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("kitchen-processing-flow")
            .fromSource("queue://kitchen-start")
            .process("cook", (io, c) -> { })
            .build());

        try {
            pipeliteContext.start();
            Assert.fail("expected the context not to start");
        } catch (ContextValidationException expected) {
            Assert.assertEquals(List.of(
                "Flow 'order-ingress-flow', toSink(...): target 'queue://kicthen-start' has no registered flow declaring fromSource(\"queue://kicthen-start\")",
                "Flow 'order-ingress-flow', wireTap(...): target 'queue://missing-audit' has no registered flow declaring fromSource(\"queue://missing-audit\")"),
                expected.getProblems());
        }

        // Validation runs before registerFlows(): not even the valid flow was registered, so no
        // consumer exists and nothing was recovered from a durable inbox.
        Assert.assertTrue(pipeliteContext.tryFindFlow("kitchen-start").isEmpty());
    }

    /**
     * Issue #101: {@code queue://dup-src} must reach exactly one flow. Without the check the flow
     * registry (first registered wins) and the link adapter (last wins) disagreed about which one.
     */
    @Test
    public void givenTwoInternalFlowsWithTheSameSourceName_whenTheContextStarts_thenItFailsNamingBothAndStartsNothing() {

        final List<String> seen = new CopyOnWriteArrayList<>();
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("dup-first")
            .fromSource("queue://dup-src")
            .process("cap", (io, c) -> seen.add("first"))
            .build());
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("dup-second")
            .fromSource("queue://dup-src")
            .process("cap", (io, c) -> seen.add("second"))
            .build());

        try {
            pipeliteContext.start();
            Assert.fail("expected the context not to start");
        } catch (ContextValidationException expected) {
            Assert.assertEquals(List.of(
                "Flow 'dup-second', fromSource(\"queue://dup-src\"): queue 'dup-src' is already read by flow 'dup-first'; scale it with concurrency instead of declaring a second flow"),
                expected.getProblems());
        }

        Assert.assertTrue(pipeliteContext.tryFindFlow("dup-src").isEmpty());
        Assert.assertTrue(seen.isEmpty());
    }

    /**
     * A source resource shared with anything but another internal source is not a conflict: here an
     * internal flow and a {@code time://} flow share a name, as {@code http://orders} and an internal
     * {@code orders} would (issue #108 keeps what belongs to a flow apart).
     */
    @Test
    public void givenAnInternalSourceSharingItsNameWithAnotherProtocol_whenTheContextStarts_thenItStarts() {

        final List<String> cooked = new CopyOnWriteArrayList<>();
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("time-flow")
            .fromSource("time://shared-name?period=3600000&timeUnit=MILLISECONDS")
            .process("noop", (io, c) -> { })
            .build());
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("internal-flow")
            .fromSource("queue://shared-name")
            .process("cook", (io, c) -> cooked.add(io.getInputPayloadAs(String.class)))
            .build());

        pipeliteContext.start();
        pipeliteContext.supplyExchange("queue://shared-name", pipeliteContext.getExchangeFactory().createExchange("order-1"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> cooked.size() == 1);
        Assert.assertEquals(List.of("order-1"), cooked);
    }

    /**
     * A validator added from outside runs after the built-in ones, sees the flows through the same
     * read-only view, and what it reports is listed in the same exception.
     */
    @Test
    public void givenAValidatorAddedToTheContext_whenTheContextStarts_thenItsErrorsAreListedWithTheBuiltInOnes() {

        ((DefaultPipeliteContext) pipeliteContext).addContextValidator((context, report) -> {
            for (FlowDefinition flow : context.flowDefinitions()) {
                if (!flow.getFlowName().endsWith("-flow")) {
                    report.error(flow.getFlowName(), "naming: a flow name must end with '-flow'");
                }
            }
        });

        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("ingress")
            .fromSource("queue://ingress-start")
            .toSink("queue://kicthen-start")
            .build());
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("kitchen-flow")
            .fromSource("queue://kitchen-start")
            .build());

        try {
            pipeliteContext.start();
            Assert.fail("expected the context not to start");
        } catch (ContextValidationException expected) {
            Assert.assertEquals(List.of(
                "Flow 'ingress', toSink(...): target 'queue://kicthen-start' has no registered flow declaring fromSource(\"queue://kicthen-start\")",
                "Flow 'ingress', naming: a flow name must end with '-flow'"),
                expected.getProblems());
        }
    }

    @Test
    public void givenAValidatorThatOnlyWarns_whenTheContextStarts_thenItStarts() {

        final List<String> warned = new CopyOnWriteArrayList<>();
        final ContextValidator warning = (context, report) -> {
            warned.add("ran");
            report.warn(null, "worth a look, not worth stopping for");
        };
        ((DefaultPipeliteContext) pipeliteContext).addContextValidator(warning);
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("only-flow").fromSource("queue://only-start").build());

        pipeliteContext.start();

        Assert.assertEquals(List.of("ran"), warned);
    }

    @Test
    public void givenEveryQueueIsReadByAFlow_whenTheContextStarts_thenExchangesFlowAcrossThem() {

        final List<String> cooked = new CopyOnWriteArrayList<>();
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("order-ingress-flow")
            .fromSource("queue://ingress-start")
            .toSink("queue://kitchen-start")
            .build());
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("kitchen-processing-flow")
            .fromSource("queue://kitchen-start")
            .process("cook", (io, c) -> cooked.add(io.getInputPayloadAs(String.class)))
            .build());

        pipeliteContext.start();
        pipeliteContext.supplyExchange("queue://ingress-start", pipeliteContext.getExchangeFactory().createExchange("order-1"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> cooked.size() == 1);
        Assert.assertEquals(List.of("order-1"), cooked);
    }

}
