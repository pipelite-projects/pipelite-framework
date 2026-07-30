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
import io.pipelite.core.context.UnsupportedSourceConcurrencyException;
import io.pipelite.dsl.definition.FlowDefinition;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Covers the two pieces of the {@code fromSource} concurrency feature that don't need a running
 * flow to exercise: the shared source worker pool's sizing ({@link
 * DefaultPipeliteContext#setMaxSourceWorkerPoolSize}) and the fail-fast rejection of {@code
 * concurrency}/{@code executorType} on any {@code fromSource}/{@code toSink} URL resolved through
 * a channel adapter. End-to-end concurrent processing itself is covered by {@code
 * PipeliteSourceConcurrencyIntegrationTest}.
 */
public class SourceConcurrencyConfigurationTest {

    private DefaultPipeliteContext context;

    @After
    public void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    public void shouldUseDefaultSharedSourceWorkerPoolSizeWhenNotConfigured() {
        context = new DefaultPipeliteContext();
        context.start();

        final ExecutorService pool = context.getSourceWorkerPool();
        Assert.assertTrue(pool instanceof ThreadPoolExecutor);
        Assert.assertEquals(200, ((ThreadPoolExecutor) pool).getMaximumPoolSize());
    }

    @Test
    public void shouldSizeSharedSourceWorkerPoolFromConfiguration() {
        context = new DefaultPipeliteContext();
        context.setMaxSourceWorkerPoolSize(7);
        context.start();

        final ExecutorService pool = context.getSourceWorkerPool();
        Assert.assertTrue(pool instanceof ThreadPoolExecutor);
        Assert.assertEquals(7, ((ThreadPoolExecutor) pool).getMaximumPoolSize());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNonPositiveMaxSourceWorkerPoolSize() {
        context = new DefaultPipeliteContext();
        context.setMaxSourceWorkerPoolSize(0);
    }

    @Test
    public void shouldLazilyCreateSourceWorkerPoolBeforeStart() {
        // FlowFactory#createFlow can be exercised directly in tests without ever calling
        // start() (see FlowFactoryTest) — getSourceWorkerPool() must stay safe to call early,
        // not require start() to have already run.
        context = new DefaultPipeliteContext();
        context.setMaxSourceWorkerPoolSize(3);

        final ExecutorService pool = context.getSourceWorkerPool();

        Assert.assertTrue(pool instanceof ThreadPoolExecutor);
        Assert.assertEquals(3, ((ThreadPoolExecutor) pool).getMaximumPoolSize());
        Assert.assertSame("repeated calls must return the same pool instance, not recreate it", pool, context.getSourceWorkerPool());
    }

    @Test(expected = UnsupportedSourceConcurrencyException.class)
    public void shouldRejectConcurrencyOnChannelAdapterBackedSource() {
        context = new DefaultPipeliteContext();
        final FlowDefinition flow = Pipelite.defineFlow("http-flow-with-concurrency")
            .fromSource("http://ingress?concurrency=4")
            .toSink("slf4j://main-logger")
            .build();
        context.registerFlowDefinition(flow);
        context.start();
    }

    @Test(expected = UnsupportedSourceConcurrencyException.class)
    public void shouldRejectExecutorTypeOnChannelAdapterBackedSource() {
        context = new DefaultPipeliteContext();
        final FlowDefinition flow = Pipelite.defineFlow("http-flow-with-executor-type")
            .fromSource("http://ingress?executorType=BOUNDED_POOL")
            .toSink("slf4j://main-logger")
            .build();
        context.registerFlowDefinition(flow);
        context.start();
    }

    @Test
    public void shouldAllowConcurrencyOnNoProtocolSource() {
        context = new DefaultPipeliteContext();
        final FlowDefinition flow = Pipelite.defineFlow("internal-flow-with-concurrency")
            .fromSource("internal-source?concurrency=4")
            .toSink("slf4j://main-logger")
            .build();
        context.registerFlowDefinition(flow);
        context.start(); // must not throw
    }

    @Test
    public void shouldAllowFileEndpointWithoutConcurrencyParams() {
        // Regression guard for the risk flagged during planning: DefaultEndpointFactory's
        // fail-fast check must not call EndpointURL.parse with the default resource pattern,
        // or a legitimate file:// URL (wider resource pattern) would be falsely rejected.
        context = new DefaultPipeliteContext();
        final FlowDefinition flow = Pipelite.defineFlow("file-flow")
            .fromSource("file:///tmp/pipelite-test-input")
            .toSink("slf4j://main-logger")
            .build();
        context.registerFlowDefinition(flow);
        context.start(); // must not throw
    }

}
