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
package io.pipelite.core.context.impl;

import io.pipelite.core.Pipelite;
import io.pipelite.core.config.EndpointURLPropertyResolver;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import org.junit.Test;

/**
 * With the default (plain-Java) {@code NoOpEndpointURLPropertyResolver}, an unresolved
 * {@code ${key}} placeholder is never substituted, so it reaches {@code EndpointURL}/
 * {@code ChannelURL} parsing unchanged and fails there with a generic malformed-url error —
 * an explicit failure, never a silently-literal placeholder in the running endpoint.
 */
public class EndpointURLPropertyPlaceholderTest {

    @Test(expected = IllegalArgumentException.class)
    public void givenUnresolvedPlaceholderInUrl_whenContextStarts_thenThrowIllegalArgumentException(){

        final PipeliteContext context = Pipelite.createContext();

        final FlowDefinition flow = Pipelite.defineFlow("placeholder-flow")
            .fromSource("origin-${properties.inputFolder}")
            .toSink("slf4j://main-logger")
            .build();

        context.registerFlowDefinition(flow);
        context.start();
    }

    /**
     * Regression test for {@code FlowFactory.createFlow}: the {@code Flow} used for internal
     * routing ({@code DefaultFlowRegistry}, {@code LinkChannelAdapter}) must be keyed on the
     * <em>resolved</em> source resource, not the raw pre-substitution URL — otherwise a
     * {@code supplyExchange(...)} call with the real (resolved) destination name would never
     * match, and any {@code ChannelAdapter.onFlowRegistered} hook re-parsing the raw URL would
     * choke on the still-literal {@code ${key}}.
     */
    @Test
    public void givenResolvedPlaceholderInUrl_whenContextStarts_thenFlowIsRoutableUnderResolvedResourceName(){

        final EndpointURLPropertyResolver resolver = rawUrl -> rawUrl.replace("${key}", "resolved-source");
        final PipeliteContext context = Pipelite.createContext(resolver);

        final FlowDefinition flow = Pipelite.defineFlow("placeholder-flow")
            .fromSource("${key}")
            .toSink("slf4j://main-logger")
            .build();

        context.registerFlowDefinition(flow);
        context.start();

        final ExchangeFactory exchangeFactory = context.getExchangeFactory();
        // does not throw "Unrecognized destination": proves the flow is routable under the
        // resolved resource name, not under the literal, unresolved placeholder.
        context.supplyExchange("resolved-source", exchangeFactory.createExchange("Hello Pipelite!"));
    }

}
