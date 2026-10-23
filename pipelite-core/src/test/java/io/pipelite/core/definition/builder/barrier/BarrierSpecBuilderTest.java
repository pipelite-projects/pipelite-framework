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
package io.pipelite.core.definition.builder.barrier;

import io.pipelite.dsl.barrier.BarrierConfigurator;
import io.pipelite.dsl.barrier.Correlation;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

/** Issue #150: a barrier that could never work is refused when the flow is defined. */
public class BarrierSpecBuilderTest {

    private static BarrierSpec build(BarrierConfigurator configurator) {
        final BarrierSpecBuilder builder = new BarrierSpecBuilder();
        configurator.configure(builder);
        return builder.build();
    }

    private static void assertRefused(BarrierConfigurator configurator, String fragment) {
        try {
            build(configurator);
            Assert.fail("the barrier should have been refused, saying: " + fragment);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(fragment));
        }
    }

    @Test
    public void givenASharedKey_thenEveryAwaitUsesIt() {
        final BarrierSpec spec = build(b -> b
            .correlateBy(c -> c.headerName("order-id"))
            .await("queue://step-b")
            .await("queue://step-c")
            .timeout(Duration.ofHours(2)));

        Assert.assertEquals(Duration.ofHours(2), spec.getTimeout());
        Assert.assertEquals(2, spec.getAwaits().size());
        Assert.assertEquals("step-b", spec.getAwaits().get(0).queueName());
        Assert.assertEquals("queue://step-b", spec.getAwaits().get(0).flowURL());
        Assert.assertEquals(Correlation.ofHeader("order-id"), spec.getAwaits().get(0).correlation());
        Assert.assertEquals(Correlation.ofHeader("order-id"), spec.getAwaits().get(1).correlation());
    }

    @Test
    public void givenAnAwaitWithAKeyOfItsOwn_thenItKeepsItAndTheOthersUseTheSharedOne() {
        final BarrierSpec spec = build(b -> b
            .correlateBy(c -> c.headerName("order-id"))
            .await("queue://step-b")
            .await("queue://step-c", c -> c.expression("Headers['customer']"))
            .timeout(Duration.ofMinutes(5)));

        Assert.assertEquals(Correlation.ofHeader("order-id"), spec.getAwaits().get(0).correlation());
        Assert.assertEquals(Correlation.ofExpression("Headers['customer']"), spec.getAwaits().get(1).correlation());
    }

    @Test
    public void givenEveryAwaitHasItsOwnKey_thenNoSharedKeyIsNeeded() {
        final BarrierSpec spec = build(b -> b
            .await("queue://step-b", c -> c.headerName("order-id"))
            .await("queue://step-c", c -> c.headerName("customer-id"))
            .timeout(Duration.ofMinutes(5)));

        Assert.assertEquals(List.of(Correlation.ofHeader("order-id"), Correlation.ofHeader("customer-id")),
            spec.getAwaits().stream().map(BarrierSpec.Await::correlation).toList());
    }

    @Test
    public void givenAQueueUrlWithQueryParameters_thenTheQueueNameIsTheResource() {
        final BarrierSpec spec = build(b -> b
            .await("queue://step-b?concurrency=4", c -> c.headerName("k"))
            .timeout(Duration.ofMinutes(1)));

        Assert.assertEquals("step-b", spec.getAwaits().get(0).queueName());
    }

    @Test
    public void givenNothingToAwait_thenItIsRefused() {
        assertRefused(b -> b.correlateBy(c -> c.headerName("k")).timeout(Duration.ofMinutes(1)), "at least one await");
    }

    @Test
    public void givenNoTimeout_thenItIsRefusedBecauseAnExchangeCouldWaitForever() {
        assertRefused(b -> b.correlateBy(c -> c.headerName("k")).await("queue://step-b"), "timeout");
    }

    @Test
    public void givenAnAwaitWithoutAnyKey_thenItIsRefusedAndNamesTheAwait() {
        assertRefused(b -> b
            .await("queue://step-b", c -> c.headerName("k"))
            .await("queue://step-c")
            .timeout(Duration.ofMinutes(1)), "await(\"queue://step-c\") has no key");
    }

    @Test
    public void givenTheSameFlowAwaitedTwice_thenItIsRefused() {
        assertRefused(b -> b
            .correlateBy(c -> c.headerName("k"))
            .await("queue://step-b")
            .await("queue://step-b?concurrency=2")
            .timeout(Duration.ofMinutes(1)), "awaited twice");
    }

    @Test
    public void givenABareName_thenItIsRefusedWithTheUrlToWrite() {
        assertRefused(b -> b.await("step-b", c -> c.headerName("k")), "queue://step-b");
    }

    @Test
    public void givenAFlowThatIsNotReadFromAQueue_thenItIsRefused() {
        assertRefused(b -> b.await("kafka://orders", c -> c.headerName("k")), "only a flow that reads a queue");
    }

    @Test
    public void givenABlankOrMissingUrl_thenItIsRefused() {
        assertRefused(b -> b.await(" "), "needs the URL of the flow");
        assertRefused(b -> b.await(null), "needs the URL of the flow");
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenAZeroTimeout_thenItIsRefused() {
        new BarrierSpecBuilder().timeout(Duration.ZERO);
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenABlankHeaderName_thenItIsRefused() {
        new BarrierSpecBuilder().correlateBy(c -> c.headerName(" "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenABlankExpression_thenItIsRefused() {
        new BarrierSpecBuilder().correlateBy(c -> c.expression(""));
    }
}
