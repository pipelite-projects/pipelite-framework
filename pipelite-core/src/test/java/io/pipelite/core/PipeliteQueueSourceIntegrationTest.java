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

import io.pipelite.core.config.EndpointURLPropertyResolver;
import io.pipelite.core.context.PipeliteContext;
import io.pipelite.core.context.UnsupportedSourceConcurrencyException;
import io.pipelite.spi.endpoint.SourceConcurrencyConfigurer;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Issue #111: the entrance of an internal flow is a queue, {@code queue://<name>}, and a source is
 * always a URL. A bare name is not a source, whether it is written in the DSL or comes out of a
 * placeholder; {@code concurrency} is what a queue has and nothing else does.
 */
public class PipeliteQueueSourceIntegrationTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private String previousHome;
    private PipeliteContext pipeliteContext;

    @Before
    public void setup() throws Exception {
        previousHome = System.getProperty("pipelite.home");
        System.setProperty("pipelite.home", temporaryFolder.newFolder("home").toPath().toString());
    }

    @After
    public void tearDown() {
        if (pipeliteContext != null) {
            pipeliteContext.stop();
        }
        if (previousHome != null) {
            System.setProperty("pipelite.home", previousHome);
        } else {
            System.clearProperty("pipelite.home");
        }
    }

    @Test
    public void givenABareNameAsSource_whenTheFlowIsDefined_thenItIsRejectedSuggestingTheQueue() {
        try {
            Pipelite.defineFlow("bare-flow").fromSource("orders");
            Assert.fail("expected a bare name not to be a source");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("Write 'queue://orders'"));
        }
    }

    @Test
    public void givenABareNameAsSourceWithAConfigurer_whenTheFlowIsDefined_thenItIsRejectedToo() {
        try {
            Pipelite.defineFlow("bare-flow").fromSource("orders", (SourceConcurrencyConfigurer c) -> c.concurrency(2));
            Assert.fail("expected a bare name not to be a source");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("Write 'queue://orders'"));
        }
    }

    /**
     * The DSL cannot see a value that comes out of a placeholder: the endpoint is created from the
     * resolved URL, and refuses it there.
     */
    @Test
    public void givenAPlaceholderThatResolvesToABareName_whenTheContextStarts_thenItFailsNamingTheSource() {
        final EndpointURLPropertyResolver resolver = rawUrl -> rawUrl.replace("${source}", "orders");
        pipeliteContext = Pipelite.createContext(resolver);
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("placeholder-flow")
            .fromSource("${source}")
            .build());

        try {
            pipeliteContext.start();
            Assert.fail("expected the context not to start");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("fromSource(\"orders\"): 'orders' is not a URL"));
        }
    }

    /**
     * A queue is read by one flow through as many consumers as it declares: {@code concurrency} is
     * accepted on {@code queue://}, and only there.
     */
    @Test
    public void givenAQueueWithConcurrency_whenExchangesAreSupplied_thenItsConsumersRunThemAtTheSameTime() {
        pipeliteContext = Pipelite.createContext();
        final AtomicInteger running = new AtomicInteger();
        final AtomicInteger mostAtOnce = new AtomicInteger();
        final AtomicInteger done = new AtomicInteger();
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("consumer-flow")
            .fromSource("queue://work", (SourceConcurrencyConfigurer c) -> c.concurrency(3))
            .process("hold", (exchange, contribution) -> {
                mostAtOnce.accumulateAndGet(running.incrementAndGet(), Math::max);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                running.decrementAndGet();
                done.incrementAndGet();
            })
            .build());
        pipeliteContext.start();

        for (int i = 0; i < 3; i++) {
            pipeliteContext.supplyExchange("queue://work", pipeliteContext.getExchangeFactory().createExchange("job-" + i));
        }

        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> done.get() == 3);
        Assert.assertEquals("the three consumers of the one queue held a job each at once", 3, mostAtOnce.get());
    }

    @Test
    public void givenConcurrencyOnASourceThatIsNotAQueue_whenTheContextStarts_thenItIsRejected() {
        pipeliteContext = Pipelite.createContext();
        pipeliteContext.registerFlowDefinition(Pipelite.defineFlow("time-flow")
            .fromSource("time://tick?period=1000&concurrency=2")
            .build());

        try {
            pipeliteContext.start();
            Assert.fail("expected the context not to start");
        } catch (UnsupportedSourceConcurrencyException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("concurrency only applies to a fromSource(\"queue://...\")"));
        }
    }

}
