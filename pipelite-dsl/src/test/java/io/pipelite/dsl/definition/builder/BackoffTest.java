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
package io.pipelite.dsl.definition.builder;

import org.junit.Assert;
import org.junit.Test;

import java.time.Duration;

/**
 * Issue #95: {@link Backoff#delayBeforeAttempt(int)} is the first real behavior this DSL module
 * carries (everything else here is interfaces/value types), hence its own test rather than
 * relying only on {@code RetryChannelExceptionHandlerTest} in pipelite-core.
 */
public class BackoffTest {

    @Test
    public void linearGrowsByAConstantStep() {
        final Backoff backoff = Backoff.linear(Duration.ofSeconds(2));
        Assert.assertEquals(Duration.ofSeconds(2), backoff.delayBeforeAttempt(2));
        Assert.assertEquals(Duration.ofSeconds(4), backoff.delayBeforeAttempt(3));
        Assert.assertEquals(Duration.ofSeconds(6), backoff.delayBeforeAttempt(4));
    }

    @Test
    public void exponentialDoublesEveryAttempt() {
        final Backoff backoff = Backoff.exponential(Duration.ofSeconds(2));
        Assert.assertEquals(Duration.ofSeconds(2), backoff.delayBeforeAttempt(2));
        Assert.assertEquals(Duration.ofSeconds(4), backoff.delayBeforeAttempt(3));
        Assert.assertEquals(Duration.ofSeconds(8), backoff.delayBeforeAttempt(4));
        Assert.assertEquals(Duration.ofSeconds(16), backoff.delayBeforeAttempt(5));
    }

    @Test
    public void rejectsAnAttemptNumberBelowTwo() {
        final Backoff backoff = Backoff.linear(Duration.ofSeconds(2));
        try {
            backoff.delayBeforeAttempt(1);
            Assert.fail("expected an IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // attempt 1 is the original, un-retried send - no backoff ever applies to it
        }
    }

}
