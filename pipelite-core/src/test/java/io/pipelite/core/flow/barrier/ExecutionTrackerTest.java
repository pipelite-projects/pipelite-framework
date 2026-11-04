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
package io.pipelite.core.flow.barrier;

import io.pipelite.common.support.serialization.ByteArrayToObjectConverter;
import io.pipelite.common.support.serialization.ObjectToByteArrayConverter;
import io.pipelite.core.context.internal.IOKeys;
import io.pipelite.core.exchange.internal.ExchangeImpl;
import io.pipelite.core.exchange.internal.SimpleMessage;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

/** Issue #150: what became of a run of a flow that a barrier awaits, and what is told about it. */
public class ExecutionTrackerTest {

    private static final Set<Pending> PENDINGS = Set.of(new Pending("step-b", "o-1"));

    private static ExchangeImpl exchangeWith(ExecutionTracker tracker) {
        final ExchangeImpl exchange = new ExchangeImpl(new SimpleMessage("message-1"));
        if (tracker != null) {
            exchange.setProperty(IOKeys.EXECUTION_TRACKER_PROPERTY_NAME, tracker);
        }
        return exchange;
    }

    @Test
    public void givenARunThatEndsWithNothingHandled_thenItCompleted() {
        Assert.assertEquals(ExecutionTracker.Outcome.COMPLETED, new ExecutionTracker(PENDINGS).settle());
    }

    @Test
    public void givenAFailureHandedToTheErrorHandling_thenItFailed() {
        final ExecutionTracker tracker = new ExecutionTracker(PENDINGS);
        ExecutionTracker.markFailed(exchangeWith(tracker));

        Assert.assertEquals(ExecutionTracker.Outcome.FAILED, tracker.settle());
    }

    @Test
    public void givenAFailureThatWasSavedToBeRetried_thenNothingIsToldYet() {
        final ExecutionTracker tracker = new ExecutionTracker(PENDINGS);
        final ExchangeImpl exchange = exchangeWith(tracker);
        // the order of a handled failure: the node marks it failed, then the retry handler says it will be retried
        ExecutionTracker.markFailed(exchange);
        ExecutionTracker.markRetrying(exchange);

        Assert.assertEquals(ExecutionTracker.Outcome.NONE, tracker.settle());
    }

    @Test
    public void givenTheExchangeWaitsAtABarrierOfItsOwn_thenTheRunHasNotEnded() {
        final ExecutionTracker tracker = new ExecutionTracker(PENDINGS);
        ExecutionTracker.markWaiting(exchangeWith(tracker));

        Assert.assertEquals(ExecutionTracker.Outcome.NONE, tracker.settle());
    }

    @Test
    public void givenARunWasSettled_thenItIsNotToldASecondTimeAndLaterMarksChangeNothing() {
        final ExecutionTracker tracker = new ExecutionTracker(PENDINGS);
        Assert.assertEquals(ExecutionTracker.Outcome.COMPLETED, tracker.settle());

        ExecutionTracker.markFailed(exchangeWith(tracker));

        Assert.assertEquals(ExecutionTracker.Outcome.NONE, tracker.settle());
        Assert.assertEquals(ExecutionTracker.Outcome.NONE, tracker.settleExhausted());
    }

    @Test
    public void givenTheAttemptsAreExhausted_thenItFailedOnce() {
        final ExecutionTracker tracker = new ExecutionTracker(PENDINGS);

        Assert.assertEquals(ExecutionTracker.Outcome.FAILED, tracker.settleExhausted());
        Assert.assertEquals(ExecutionTracker.Outcome.NONE, tracker.settleExhausted());
    }

    @Test
    public void givenNoTracker_thenMarkingIsHarmless() {
        final ExchangeImpl exchange = exchangeWith(null);

        ExecutionTracker.markFailed(exchange);
        ExecutionTracker.markRetrying(exchange);
        ExecutionTracker.markWaiting(exchange);

        Assert.assertNull(ExecutionTracker.of(exchange));
    }

    @Test
    public void givenTheExchangeIsWrittenAwayAndReadBack_thenTheKeysSurviveAndTheRunStartsClean() {
        final ExecutionTracker tracker = new ExecutionTracker(PENDINGS);
        ExecutionTracker.markFailed(exchangeWith(tracker));

        final ExecutionTracker readBack = new ByteArrayToObjectConverter()
            .convert(new ObjectToByteArrayConverter().convert(tracker), ExecutionTracker.class);

        Assert.assertEquals(PENDINGS, readBack.pendings());
        Assert.assertEquals("a retry is a new run: what was handled in the one before does not count",
            ExecutionTracker.Outcome.COMPLETED, readBack.settle());
    }
}
