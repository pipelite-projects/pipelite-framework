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
    public void givenALinkNoFlowDeclares_whenTheContextStarts_thenItFailsListingEveryProblemAndStartsNothing() {

        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("order-ingress-flow")
            .fromSource("http://orders")
            .wireTap("audit", "link://missing-audit")
            .toSink("link://kicthen-start")   // typo: the real source is "kitchen-start"
            .build());
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("kitchen-processing-flow")
            .fromSource("kitchen-start")
            .process("cook", (io, c) -> { })
            .build());

        try {
            pipeliteContext.start();
            Assert.fail("expected the context not to start");
        } catch (ContextValidationException expected) {
            Assert.assertEquals(List.of(
                "Flow 'order-ingress-flow', toSink(...): target 'link://kicthen-start' has no registered flow declaring fromSource(\"kicthen-start\")",
                "Flow 'order-ingress-flow', wireTap(...): target 'link://missing-audit' has no registered flow declaring fromSource(\"missing-audit\")"),
                expected.getProblems());
        }

        // Validation runs before registerFlows(): not even the valid flow was registered, so no
        // consumer exists and nothing was recovered from a durable inbox.
        Assert.assertTrue(pipeliteContext.tryFindFlow("kitchen-start").isEmpty());
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
            .fromSource("ingress-start")
            .toSink("link://kicthen-start")
            .build());
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("kitchen-flow")
            .fromSource("kitchen-start")
            .build());

        try {
            pipeliteContext.start();
            Assert.fail("expected the context not to start");
        } catch (ContextValidationException expected) {
            Assert.assertEquals(List.of(
                "Flow 'ingress', toSink(...): target 'link://kicthen-start' has no registered flow declaring fromSource(\"kicthen-start\")",
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
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("only-flow").fromSource("only-start").build());

        pipeliteContext.start();

        Assert.assertEquals(List.of("ran"), warned);
    }

    @Test
    public void givenEveryLinkPointsAtAFlow_whenTheContextStarts_thenExchangesFlowAcrossThem() {

        final List<String> cooked = new CopyOnWriteArrayList<>();
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("order-ingress-flow")
            .fromSource("ingress-start")
            .toSink("link://kitchen-start")
            .build());
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("kitchen-processing-flow")
            .fromSource("kitchen-start")
            .process("cook", (io, c) -> cooked.add(io.getInputPayloadAs(String.class)))
            .build());

        pipeliteContext.start();
        pipeliteContext.supplyExchange("link://ingress-start", pipeliteContext.getExchangeFactory().createExchange("order-1"));

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> cooked.size() == 1);
        Assert.assertEquals(List.of("order-1"), cooked);
    }

}
