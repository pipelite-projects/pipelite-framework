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
package io.pipelite.test;

import io.pipelite.core.Pipelite;
import io.pipelite.core.config.FlowConfigurationException;
import io.pipelite.dsl.annotation.DefineFlow;
import io.pipelite.dsl.annotation.FlowConfiguration;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.test.support.matchers.Actions;
import io.pipelite.test.support.matchers.Expectations;
import io.pipelite.test.support.matchers.Preconditions;
import org.junit.Test;

public class PipeliteTestFixtureFlowConfigurationTest {

    // -------------------------------------------------------------------------
    // @FlowConfiguration with no dependencies
    // -------------------------------------------------------------------------

    @FlowConfiguration
    static class UppercaseFlowConfiguration {

        @DefineFlow
        FlowDefinition uppercaseFlow() {
            return Pipelite.defineFlow("uppercase-flow")
                .fromSource("words-in")
                .process("uppercase", (io, c) ->
                    io.setOutputPayload(io.getInputPayloadAs(String.class).toUpperCase()))
                .toSink("words-out")
                .build();
        }
    }

    @Test
    public void givenFlowConfiguration_whenSupplyTo_thenFlowIsDiscoveredAndExecuted() {
        PipeliteTestFixture.given(
                Preconditions.flowConfiguration(UppercaseFlowConfiguration.class),
                Preconditions.inputPayload("hello"))
            .when(Actions.supplyTo("words-in"))
            .then(Expectations.isExecutionCompleted(), Expectations.payloadEquals("HELLO"));
    }

    @Test
    public void givenFlowConfigurationWithMultipleFlows_whenSupplyTo_thenAllFlowsAreAvailable() {
        PipeliteTestFixture.given(
                Preconditions.flowConfiguration(UppercaseFlowConfiguration.class),
                Preconditions.inputPayload("world"))
            .when(Actions.supplyTo("words-in"))
            .then(Expectations.isExecutionCompleted())
            .inspectStep("uppercase", Expectations.payloadEquals("WORLD"));
    }

    // -------------------------------------------------------------------------
    // @FlowConfiguration with an injected dependency
    // -------------------------------------------------------------------------

    static class GreetingService {
        String greet(String name) {
            return "Hello, " + name + "!";
        }
    }

    @FlowConfiguration
    static class GreetingFlowConfiguration {

        @DefineFlow
        FlowDefinition greetingFlow(GreetingService greetingService) {
            return Pipelite.defineFlow("greeting-flow")
                .fromSource("names-in")
                .process("greet", (io, c) ->
                    io.setOutputPayload(greetingService.greet(io.getInputPayloadAs(String.class))))
                .toSink("greetings-out")
                .build();
        }
    }

    @Test
    public void givenFlowConfigurationWithDependency_whenSupplyTo_thenDependencyIsInjectedAndFlowExecutes() {
        PipeliteTestFixture.given(
                Preconditions.flowConfiguration(GreetingFlowConfiguration.class, new GreetingService()),
                Preconditions.inputPayload("Alice"))
            .when(Actions.supplyTo("names-in"))
            .then(Expectations.isExecutionCompleted(), Expectations.payloadEquals("Hello, Alice!"));
    }

    // -------------------------------------------------------------------------
    // Two @FlowConfiguration classes — flows linked via link://
    // -------------------------------------------------------------------------

    @FlowConfiguration
    static class NormalizationFlowConfiguration {

        @DefineFlow
        FlowDefinition normalizationFlow() {
            return Pipelite.defineFlow("normalization-flow")
                .fromSource("raw-in")
                .process("normalize", (io, c) ->
                    io.setOutputPayload(io.getInputPayloadAs(String.class).trim().toLowerCase()))
                .toSink("link://enriched-in")
                .build();
        }
    }

    @FlowConfiguration
    static class EnrichmentFlowConfiguration {

        @DefineFlow
        FlowDefinition enrichmentFlow(GreetingService greetingService) {
            return Pipelite.defineFlow("enrichment-flow")
                .fromSource("enriched-in")
                .process("enrich", (io, c) ->
                    io.setOutputPayload(greetingService.greet(io.getInputPayloadAs(String.class))))
                .toSink("enriched-out")
                .build();
        }
    }

    @Test
    public void givenTwoFlowConfigurations_whenSupplyTo_thenBothAreDeployedAndFlowsChainCorrectly() {
        PipeliteTestFixture.given(
                Preconditions.flowConfiguration(NormalizationFlowConfiguration.class),
                Preconditions.flowConfiguration(EnrichmentFlowConfiguration.class, new GreetingService()),
                Preconditions.inputPayload("  ALICE  "))
            .when(Actions.supplyTo("raw-in"))
            .then(Expectations.isExecutionCompleted(), Expectations.payloadEquals("Hello, alice!"))
            .inspectStep("normalize", Expectations.payloadEquals("alice"))
            .inspectStep("enrich", Expectations.payloadEquals("Hello, alice!"));
    }

    // -------------------------------------------------------------------------
    // Guard: class not annotated with @FlowConfiguration
    // -------------------------------------------------------------------------

    static class NotAFlowConfiguration {
        @DefineFlow
        FlowDefinition someFlow() {
            return Pipelite.defineFlow("ignored-flow").fromSource("x").toSink("y").build();
        }
    }

    @Test(expected = FlowConfigurationException.class)
    public void givenClassWithoutAnnotation_whenFlowConfiguration_thenThrowsFlowConfigurationException() {
        PipeliteTestFixture.given(
                Preconditions.flowConfiguration(NotAFlowConfiguration.class),
                Preconditions.inputPayload("x"))
            .when(Actions.supplyTo("x"));
    }
}
