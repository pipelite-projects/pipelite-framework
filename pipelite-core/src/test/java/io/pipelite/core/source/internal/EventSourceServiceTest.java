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
package io.pipelite.core.source.internal;

import io.pipelite.spi.endpoint.Endpoint;

import io.pipelite.dsl.Headers;
import io.pipelite.spi.endpoint.EndpointURL;
import io.pipelite.spi.flow.exchange.DistributedIdentityGeneratorImpl;
import io.pipelite.spi.flow.exchange.ExchangeFactory;
import io.pipelite.spi.flow.exchange.ExchangeImpl;
import io.pipelite.core.flow.internal.FlowNode;
import io.pipelite.spi.flow.exchange.SimpleMessage;
import io.pipelite.core.flow.internal.ExchangePostProcessor;
import io.pipelite.core.flow.internal.ExchangePreProcessor;
import io.pipelite.core.flow.execution.inbox.SegmentedLogDurableInbox;
import io.pipelite.spi.source.EventSource;
import io.pipelite.spi.source.SourceContext;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The framework's side of an {@link EventSource} (issues #159, #71): what {@link SourceContext#accept} promises
 * a source, kept from the tests of the Kafka consumer service that used to check it through Kafka. {@code accept}
 * returns once the exchange is durably queued, not when the flow has processed it (so a source can commit its
 * input without waiting for the pipeline), and it throws when the exchange cannot be durably written (so the
 * source does not).
 */
public class EventSourceServiceTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final class CapturingSource implements EventSource {
        volatile SourceContext context;
        volatile boolean stopped;

        @Override
        public void start(SourceContext context) {
            this.context = context;
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    private final CapturingSource source = new CapturingSource();
    private EventSourceService service;

    @Before
    public void setup() {
        service = new EventSourceService(source, (Endpoint) () -> EndpointURL.parse("test-source"), "test");
        service.setFlowName("test-flow");
        service.setSourceEndpointResource("test-source");
        service.setProcessorName("test-processor");
        service.setExchangeFactory(new TestExchangeFactory());
    }

    @After
    public void tearDown() {
        service.stop();
    }

    @Test
    public void givenTheServiceStarts_thenTheSourceIsStartedAndRunning_andWhenItStopsTheSourceIsStoppedAndNotRunning() {
        service.start();

        Assert.assertNotNull(source.context);
        Assert.assertTrue(source.context.isRunning());
        Assert.assertEquals("test-flow", source.context.flowName());

        service.stop();

        Assert.assertTrue(source.stopped);
        Assert.assertFalse(source.context.isRunning());
    }

    @Test
    public void givenAnAcceptedExchange_thenAcceptReturnsBeforeTheFlowHasProcessedIt() throws InterruptedException {
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger processed = new AtomicInteger();
        service.setNext(node(exchange -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            processed.incrementAndGet();
        }));
        service.start();

        source.context.accept(source.context.newExchange("payload"));

        Assert.assertEquals("accept must not wait for the pipeline", 0, processed.get());
        release.countDown();
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> processed.get() == 1);
    }

    @Test
    public void givenTheExchangeCannotBeDurablyWritten_thenAcceptThrowsAndTheFlowNeverRunsIt() throws IOException, InterruptedException {
        // A plain file where the inbox needs a directory: Files.createDirectories(...) fails, which stands
        // in for a real local disk I/O failure, the failure mode a source must be told about.
        final Path blockedDirectory = temporaryFolder.newFile("blocked-inbox-directory").toPath();
        service.setDurableInbox(new SegmentedLogDurableInbox(
            blockedDirectory, "test-resource", new DistributedIdentityGeneratorImpl()));
        final AtomicInteger processed = new AtomicInteger();
        service.setNext(node(exchange -> processed.incrementAndGet()));
        service.start();

        try {
            source.context.accept(source.context.newExchange("payload"));
            Assert.fail("accept must throw when the exchange cannot be durably written");
        } catch (RuntimeException expected) {
            // the source must not acknowledge its input
        }
        Thread.sleep(300);
        Assert.assertEquals(0, processed.get());
    }

    @Test
    public void givenAThreadIsNeeded_thenTheFactoryNamesItAfterTheRoleAndTheFlow() throws InterruptedException {
        service.start();

        final Thread thread = source.context.threadFactory().newThread(() -> { });

        Assert.assertTrue(thread.getName(), thread.getName().startsWith("pipelite-test-"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenAnExchangeThatTheContextDidNotCreate_thenAcceptRejectsIt() {
        service.start();

        source.context.accept(null);
    }

    private static FlowNode node(java.util.function.Consumer<ExchangeImpl> onProcess) {
        return new FlowNode() {
            @Override public void process(ExchangeImpl exchange) { onProcess.accept(exchange); }
            @Override public void setFlowName(String flowName) { }
            @Override public void setSourceEndpointResource(String sourceEndpointResource) { }
            @Override public void setProcessorName(String processorName) { }
            @Override public void setNext(FlowNode next) { }
            @Override public boolean hasNext() { return false; }
            @Override public void addExchangePreProcessor(ExchangePreProcessor exchangePreProcessor) { }
            @Override public void addExchangePostProcessor(ExchangePostProcessor exchangePostProcessor) { }
        };
    }

    private static class TestExchangeFactory implements ExchangeFactory {
        @Override public ExchangeImpl createExchange() { return createExchange(null, null); }
        @Override public ExchangeImpl createExchange(Headers headers) { return createExchange(headers, null); }
        @Override public ExchangeImpl createExchange(Object inputPayload) { return createExchange(null, inputPayload); }
        @Override
        public ExchangeImpl createExchange(Headers headers, Object inputPayload) {
            final ExchangeImpl exchange = new ExchangeImpl(new SimpleMessage(UUID.randomUUID().toString()), headers);
            if (inputPayload != null) {
                exchange.setInputPayload(inputPayload);
            }
            return exchange;
        }
        @Override public ExchangeImpl copyExchange(ExchangeImpl exchange) { throw new UnsupportedOperationException(); }
        @Override public ExchangeImpl nextExchange(ExchangeImpl current) { throw new UnsupportedOperationException(); }
    }
}
