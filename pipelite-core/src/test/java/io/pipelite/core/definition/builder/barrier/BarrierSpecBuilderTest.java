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

import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.exchange.internal.SimpleMessage;
import io.pipelite.dsl.barrier.BarrierOperations;
import io.pipelite.dsl.barrier.Release;
import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;
import java.util.Set;

/**
 * Issue #150: a barrier that could never work is refused when the flow is defined, and the key and the
 * contributor of an exchange are read as declared.
 */
public class BarrierSpecBuilderTest {

    private record Result(String orderId, String step, boolean failed) {
    }

    private static ExchangeImpl exchange(String messageId, Object payload) {
        final ExchangeImpl exchange = new ExchangeImpl(new SimpleMessage(messageId));
        exchange.setInputPayload(payload);
        return exchange;
    }

    private static BarrierOperations valid() {
        return new BarrierSpecBuilder()
            .correlateBy(c -> c.headerName("order-id"))
            .expect(2)
            .timeout(Duration.ofMinutes(5));
    }

    private static void assertRefused(BarrierOperations barrier, String fragment) {
        try {
            ((BarrierSpecBuilder) barrier).build();
            Assert.fail("the barrier should have been refused, saying: " + fragment);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(fragment));
        }
    }

    @Test
    public void givenEverythingRequired_thenTheDefaultsAreTheDocumentedOnes() {
        final BarrierSpec spec = ((BarrierSpecBuilder) valid()).build();

        Assert.assertSame(Release.all(), spec.getRelease());
        Assert.assertEquals(Duration.ofMinutes(5), spec.getTimeout());
        Assert.assertNull(spec.getExpectedContributors());
        Assert.assertEquals(2, spec.getExpectedCount());
        Assert.assertEquals("without contributorBy the contributor is the message",
            "message-1", spec.contributorOf(exchange("message-1", "payload")));
        Assert.assertFalse("without failedWhen nothing is a failure", spec.isFailure(exchange("m", "payload")));
    }

    @Test
    public void givenNoCorrelation_thenItIsRefused() {
        assertRefused(new BarrierSpecBuilder().expect(2).timeout(Duration.ofMinutes(1)), "correlateBy");
    }

    @Test
    public void givenNothingToExpect_thenItIsRefused() {
        assertRefused(new BarrierSpecBuilder().correlateBy(c -> c.headerName("k")).timeout(Duration.ofMinutes(1)), "expect");
    }

    @Test
    public void givenNoTimeout_thenItIsRefusedBecauseAnIncompleteOrderWouldWaitForever() {
        assertRefused(new BarrierSpecBuilder().correlateBy(c -> c.headerName("k")).expect(2), "timeout");
    }

    @Test
    public void givenNamesAndACount_thenItIsRefused() {
        assertRefused(valid().expect("a", "b").contributorBy(ex -> "a"), "not both");
    }

    @Test
    public void givenNamedContributorsWithoutContributorBy_thenItIsRefused() {
        assertRefused(new BarrierSpecBuilder().correlateBy(c -> c.headerName("k")).expect("a", "b")
            .timeout(Duration.ofMinutes(1)), "contributorBy");
    }

    @Test
    public void givenDuplicateOrEmptyNames_thenTheyAreRefusedAtOnce() {
        try {
            new BarrierSpecBuilder().expect("a", "a");
            Assert.fail("a name declared twice must be refused");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("twice"));
        }
        try {
            new BarrierSpecBuilder().expect("a", " ");
            Assert.fail("an empty name must be refused");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("null or empty"));
        }
        try {
            new BarrierSpecBuilder().expect();
            Assert.fail("no names must be refused");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("at least one"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenACountBelowOne_thenItIsRefused() {
        new BarrierSpecBuilder().expect(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void givenAZeroOrNegativeTimeout_thenItIsRefused() {
        new BarrierSpecBuilder().timeout(Duration.ZERO);
    }

    @Test
    public void givenNamedContributors_thenTheBarrierIsCompletedByTheseAndNoOthers() {
        final BarrierSpec spec = ((BarrierSpecBuilder) new BarrierSpecBuilder()
            .correlateBy(c -> c.payloadAttribute(Result::orderId))
            .expect("record", "check", "plan")
            .contributorBy(ex -> ex.getInputPayloadAs(Result.class).step())
            .timeout(Duration.ofHours(2))).build();

        Assert.assertTrue(spec.expects("check"));
        Assert.assertFalse(spec.expects("unknown"));
        Assert.assertFalse(spec.isCompletedBy(Set.of("record", "check")));
        Assert.assertTrue(spec.isCompletedBy(Set.of("record", "check", "plan")));
        Assert.assertEquals("check", spec.contributorOf(exchange("m", new Result("o-1", "check", false))));
    }

    @Test
    public void givenACount_thenAnyDistinctContributorsCountAndAnyNameIsExpected() {
        final BarrierSpec spec = ((BarrierSpecBuilder) valid()).build();

        Assert.assertTrue(spec.expects("whatever"));
        Assert.assertFalse(spec.isCompletedBy(Set.of("m1")));
        Assert.assertTrue(spec.isCompletedBy(Set.of("m1", "m2")));
    }

    @Test
    public void givenFailedWhen_thenOnlyTheContributionsItSelectsAreFailures() {
        final BarrierSpec spec = ((BarrierSpecBuilder) valid()
            .failedWhen(ex -> ex.getInputPayloadAs(Result.class).failed())).build();

        Assert.assertTrue(spec.isFailure(exchange("m", new Result("o", "check", true))));
        Assert.assertFalse(spec.isFailure(exchange("m", new Result("o", "check", false))));
    }

    @Test
    public void givenACorrelationByHeader_thenTheKeyIsTheHeaderAndAMissingOneFails() {
        final BarrierSpec spec = ((BarrierSpecBuilder) valid()).build();
        final ExchangeImpl withHeader = exchange("m", "payload");
        withHeader.putHeader("order-id", "o-42");

        Assert.assertEquals("o-42", spec.keyOf(withHeader));
        try {
            spec.keyOf(exchange("m", "payload"));
            Assert.fail("an exchange without the header cannot be correlated");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("order-id"));
        }
    }

    @Test
    public void givenACorrelationByPayloadAttribute_thenTheKeyIsTheAttributeAndAWrongPayloadFails() {
        final BarrierSpec spec = ((BarrierSpecBuilder) new BarrierSpecBuilder()
            .correlateBy(c -> c.payloadAttribute(Result::orderId))
            .expect(1).timeout(Duration.ofMinutes(1))).build();

        Assert.assertEquals("o-7", spec.keyOf(exchange("m", new Result("o-7", "plan", false))));
        try {
            spec.keyOf(exchange("m", "not a Result"));
            Assert.fail("a payload of another type cannot be correlated");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("java.lang.String"));
        }
        try {
            spec.keyOf(exchange("m", new Result(null, "plan", false)));
            Assert.fail("a null attribute cannot be a key");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("null"));
        }
    }

    @Test
    public void givenAContributorByThatReturnsNothing_thenTheExchangeFails() {
        final BarrierSpec spec = ((BarrierSpecBuilder) valid().contributorBy(ex -> null)).build();

        try {
            spec.contributorOf(exchange("m", "payload"));
            Assert.fail("a contributor without a name cannot be counted");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("contributorBy"));
        }
    }
}
