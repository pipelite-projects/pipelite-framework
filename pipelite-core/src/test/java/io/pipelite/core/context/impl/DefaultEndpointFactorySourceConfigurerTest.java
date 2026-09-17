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

import io.pipelite.channels.kafka.KafkaChannelAdapter;
import io.pipelite.channels.kafka.KafkaSourceConfigurer;
import io.pipelite.components.file.FileChannelAdapter;
import io.pipelite.components.file.FileSourceConfigurer;
import io.pipelite.components.http.HttpChannelAdapter;
import io.pipelite.components.http.HttpSourceConfigurer;
import io.pipelite.components.time.TimeChannelAdapter;
import io.pipelite.components.time.TimeSourceConfigurer;
import io.pipelite.core.Pipelite;
import io.pipelite.core.config.NoOpEndpointURLPropertyResolver;
import io.pipelite.core.context.ChannelAdapterManager;
import io.pipelite.core.context.UnsupportedSourceConcurrencyException;
import io.pipelite.dsl.definition.FlowDefinition;
import io.pipelite.dsl.definition.SourceDefinition;
import io.pipelite.spi.endpoint.Endpoint;
import io.pipelite.spi.endpoint.SourceConcurrencyConfigurer;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Proves the {@code fromSource(url, configurer)} mechanism end to end, across every adapter that
 * has one so far (Kafka, File, Time, plus the no-protocol/internal Link case): a real DSL call,
 * through {@code DefaultEndpointFactory}, down to the exact same {@code EndpointProperties} every
 * pre-existing query-string-based reader already consumes - see {@code SourceConfigurer}'s own
 * Javadoc for why this is a lowering, not a parallel mechanism. Real adapters are registered
 * directly, bypassing classpath scanning, since constructing any of these endpoints does no real
 * I/O (broker connection, file open, HTTP listener) - only each one's own {@code doStart()} would.
 */
public class DefaultEndpointFactorySourceConfigurerTest {

    private DefaultEndpointFactory endpointFactory;

    @Before
    public void setup() {
        final ChannelAdapterManager channelAdapterManager = new DefaultChannelAdapterManager(
            new DefaultExchangeFactory(new DefaultMessageFactory(new DistributedIdentityGeneratorImpl())));
        channelAdapterManager.registerChannelAdapter("kafka", new KafkaChannelAdapter());
        channelAdapterManager.registerChannelAdapter("file", new FileChannelAdapter());
        channelAdapterManager.registerChannelAdapter("time", new TimeChannelAdapter());
        channelAdapterManager.registerChannelAdapter("http", new HttpChannelAdapter());
        endpointFactory = new DefaultEndpointFactory(channelAdapterManager, new NoOpEndpointURLPropertyResolver());
    }

    private static SourceDefinition sourceDefinitionOf(FlowDefinition flowDefinition) {
        return flowDefinition.sourceDefinition();
    }

    @Test
    public void shouldLowerKafkaSourceConfigurerIntoTheSameQueryParametersTheAdapterAlreadyReads() {

        final FlowDefinition flowDefinition = Pipelite.defineFlow("orders-flow")
            .fromSource("kafka://orders", (KafkaSourceConfigurer c) -> c
                .groupId("orders-group")
                .autoOffsetReset("earliest"))
            .build();

        final Endpoint endpoint = endpointFactory.createEndpoint(sourceDefinitionOf(flowDefinition));

        Assert.assertEquals("orders-group", endpoint.getProperties().get("group.id"));
        Assert.assertEquals("earliest", endpoint.getProperties().get("auto.offset.reset"));
    }

    @Test
    public void shouldLowerSourceConcurrencyConfigurerForANoProtocolInternalSource() {

        final FlowDefinition flowDefinition = Pipelite.defineFlow("destination-flow")
            .fromSource("destination", (SourceConcurrencyConfigurer c) -> c
                .concurrency(5)
                .durableInbox(false))
            .build();

        final Endpoint endpoint = endpointFactory.createEndpoint(sourceDefinitionOf(flowDefinition));

        Assert.assertEquals(Integer.valueOf(5), endpoint.getProperties().getAsInteger("concurrency"));
        Assert.assertFalse(endpoint.getProperties().getAsBooleanOrDefault("durableInbox", true));
    }

    @Test
    public void shouldLowerFileSourceConfigurerIncludingTheSharedPollingParameters() {

        final FlowDefinition flowDefinition = Pipelite.defineFlow("file-flow")
            .fromSource("file:///tmp/test.log", (FileSourceConfigurer c) -> c
                .charset("UTF-8")
                .startPosition("beginning")
                .skipLines(1)
                .period(500)
                .timeUnit(TimeUnit.MILLISECONDS))
            .build();

        final Endpoint endpoint = endpointFactory.createEndpoint(sourceDefinitionOf(flowDefinition));

        Assert.assertEquals("UTF-8", endpoint.getProperties().get("charset"));
        Assert.assertEquals("beginning", endpoint.getProperties().get("startPosition"));
        Assert.assertEquals(Long.valueOf(1), endpoint.getProperties().getAsLong("skipLines"));
        Assert.assertEquals(Long.valueOf(500), endpoint.getProperties().getAsLong("period"));
        Assert.assertEquals("MILLISECONDS", endpoint.getProperties().get("timeUnit"));
    }

    @Test
    public void shouldLowerTimeSourceConfigurerForItsSharedPollingParametersOnly() {

        final FlowDefinition flowDefinition = Pipelite.defineFlow("time-flow")
            .fromSource("time://tick", (TimeSourceConfigurer c) -> c
                .initialDelay(10)
                .period(2)
                .timeUnit(TimeUnit.SECONDS)
                .batchSize(3))
            .build();

        final Endpoint endpoint = endpointFactory.createEndpoint(sourceDefinitionOf(flowDefinition));

        Assert.assertEquals(Long.valueOf(10), endpoint.getProperties().getAsLong("initialDelay"));
        Assert.assertEquals(Long.valueOf(2), endpoint.getProperties().getAsLong("period"));
        Assert.assertEquals("SECONDS", endpoint.getProperties().get("timeUnit"));
        Assert.assertEquals(Integer.valueOf(3), endpoint.getProperties().getAsInteger("batchSize"));
    }

    @Test
    public void shouldLowerHttpSourceConfigurerAllowedMethodOntoTheEndpoint() {

        final FlowDefinition flowDefinition = Pipelite.defineFlow("http-flow")
            .fromSource("http://orders", (HttpSourceConfigurer c) -> c.method("POST"))
            .build();

        final Endpoint endpoint = endpointFactory.createEndpoint(sourceDefinitionOf(flowDefinition));

        Assert.assertEquals("POST", endpoint.getProperties().get("method"));
    }

    @Test
    public void shouldFailClearlyWhenTheWrongConfigurerTypeIsSuppliedForTheUrlsProtocol() {

        // SourceConcurrencyConfigurer supplied for a kafka:// source - wrong type for this URL's adapter.
        final FlowDefinition flowDefinition = Pipelite.defineFlow("mismatched-flow")
            .fromSource("kafka://orders", (SourceConcurrencyConfigurer c) -> c.concurrency(5))
            .build();

        final SourceDefinition sourceDefinition = sourceDefinitionOf(flowDefinition);
        try {
            endpointFactory.createEndpoint(sourceDefinition);
            Assert.fail("expected the configurer type mismatch to be rejected");
        } catch (IllegalArgumentException expected) {
            // channelURL.getEndpointURL() is already protocol-stripped by this point - "orders",
            // not "kafka://orders" - see DefaultEndpointFactory#applySourceConfigurer. The message
            // names the EXPECTED type (KafkaSourceConfigurer, from the resolved adapter), not the
            // wrong one the caller actually supplied (SourceConcurrencyConfigurer).
            Assert.assertTrue(expected.getMessage().contains("orders"));
            Assert.assertTrue(expected.getMessage().contains("KafkaSourceConfigurer"));
        }
    }

    @Test
    public void shouldStillRejectALiteralConcurrencyQueryParameterOnAProtocolBackedSource() {

        // No configurer at all - concurrency/executorType typed directly into a channel-adapter
        // URL's query string must still be rejected exactly as before this mechanism existed.
        final FlowDefinition flowDefinition = Pipelite.defineFlow("literal-query-flow")
            .fromSource("kafka://orders?concurrency=5")
            .build();

        final SourceDefinition sourceDefinition = sourceDefinitionOf(flowDefinition);
        Assert.assertThrows(UnsupportedSourceConcurrencyException.class,
            () -> endpointFactory.createEndpoint(sourceDefinition));
    }

}
