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

import io.pipelite.test.support.matchers.Actions;
import io.pipelite.test.support.matchers.Expectations;
import io.pipelite.test.support.matchers.Preconditions;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class PipeliteTestFixtureBaseTest {

    // -------------------------------------------------------------------------
    // Success / failure state
    // -------------------------------------------------------------------------

    @Test
    public void givenProcessor_whenCompletesNormally_thenContributionIsSuccess() {
        PipeliteTestFixture.given(Preconditions.inputPayload("hello"))
            .when(Actions.process((ioContext, c) -> ioContext.setOutputPayload("world")))
            .then(Expectations.isSuccess());
    }

    @Test
    public void givenProcessor_whenThrowsRuntimeException_thenContributionIsFailure() {
        RuntimeException expected = new RuntimeException("boom");

        PipeliteTestFixture.given(Preconditions.inputPayload("input"))
            .when(Actions.process((ioContext, c) -> {
                throw expected;
            }))
            .then(Expectations.isFailure(), Expectations.failureCauseEquals(expected));
    }

    @Test
    public void givenProcessor_whenCallsStopExecution_thenIsExecutionStoppedAndStillSuccess() {
        PipeliteTestFixture.given(Preconditions.inputPayload("filter-me"))
            .when(Actions.process((ioContext, c) -> c.stopExecution()))
            .then(Expectations.isExecutionStopped(), Expectations.isSuccess());
    }

    // -------------------------------------------------------------------------
    // Output payload
    // -------------------------------------------------------------------------

    @Test
    public void givenProcessor_whenSetsOutputPayload_thenGetOutputPayloadReturnsIt() {
        PipeliteTestFixture.given(Preconditions.inputPayload(100))
            .when(Actions.process((ioContext, c) -> ioContext.setOutputPayload(200)))
            .then(Expectations.payloadEquals(200));
    }

    @Test
    public void givenProcessor_whenDoesNotSetOutput_thenInputPayloadIsForwarded() {
        PipeliteTestFixture.given(Preconditions.inputPayload("original"))
            .when(Actions.process((ioContext, c) -> { /* no output set */ }))
            .then(Expectations.payloadEquals("original"));
    }

    @Test
    public void givenProcessor_whenSetsMapOutput_thenGetOutputPayloadAsReturnsTypedMap() {
        Map<String, Object> transformed = new HashMap<>();
        transformed.put("price", 122.0);

        ThenOperations then = PipeliteTestFixture.given(Preconditions.inputPayload(Map.of("price", 100)))
            .when(Actions.process((ioContext, c) -> ioContext.setOutputPayload(transformed)))
            .then(Expectations.payloadEquals(transformed));

        then.getOutputPayloadAs(Map.class);
    }

    @Test(expected = ClassCastException.class)
    public void givenProcessor_whenGetOutputPayloadAsWrongType_thenThrowsClassCastException() {
        ThenOperations then = PipeliteTestFixture.given(Preconditions.inputPayload("text"))
            .when(Actions.process((ioContext, c) -> ioContext.setOutputPayload("result")));

        then.getOutputPayloadAs(Integer.class);
    }

    @Test
    public void givenNoPayload_whenExecuteProcessor_thenOutputPayloadIsNull() {
        PipeliteTestFixture.given()
            .when(Actions.process((ioContext, c) -> { /* nothing */ }))
            .then(Expectations.payloadEquals(null));
    }

    // -------------------------------------------------------------------------
    // Headers
    // -------------------------------------------------------------------------

    @Test
    public void givenHeader_whenProcessorReadsIt_thenValueIsCorrect() {
        String[] capturedValue = new String[1];

        PipeliteTestFixture.given(
                Preconditions.header("Source-System", "Legacy-API"),
                Preconditions.inputPayload("data"))
            .when(Actions.process((ioContext, c) -> capturedValue[0] = ioContext.tryGetHeader("Source-System").orElse(null)))
            .then((exchange, contribution) -> {
                if (!"Legacy-API".equals(capturedValue[0])) {
                    throw new AssertionError(String.format(
                        "Expected processor to read header value <Legacy-API> but read <%s>", capturedValue[0]));
                }
            });
    }

    @Test
    public void givenMultipleHeaders_whenExecuteProcessor_thenAllHeadersAvailableAfterExecution() {
        PipeliteTestFixture.given(
                Preconditions.header("X-Tenant", "acme"),
                Preconditions.header("X-Correlation-Id", "abc-123"),
                Preconditions.inputPayload("data"))
            .when(Actions.process((ioContext, c) -> { /* nothing */ }))
            .then(
                Expectations.headerEquals("X-Tenant", "acme"),
                Expectations.headerEquals("X-Correlation-Id", "abc-123"));
    }

    @Test
    public void givenProcessor_whenAddsHeader_thenNewHeaderIsVisibleAfterExecution() {
        PipeliteTestFixture.given(Preconditions.inputPayload("data"))
            .when(Actions.process((ioContext, c) -> ioContext.putHeader("X-Processed-By", "unit-test")))
            .then(Expectations.headerEquals("X-Processed-By", "unit-test"));
    }

    @Test
    public void givenAbsentHeader_whenGetHeaderAs_thenReturnsNull() {
        PipeliteTestFixture.given(Preconditions.inputPayload("data"))
            .when(Actions.process((ioContext, c) -> { /* nothing */ }))
            .then(Expectations.noHeader("X-Missing"));
    }

    @Test
    public void givenIntegerHeader_whenGetHeaderAs_thenReturnsTypedValue() {
        PipeliteTestFixture.given(
                Preconditions.header("Retry-Count", 3),
                Preconditions.inputPayload("data"))
            .when(Actions.process((ioContext, c) -> { /* nothing */ }))
            .then(Expectations.headerEquals("Retry-Count", 3));
    }

    // -------------------------------------------------------------------------
    // Expectations.payloadAs — custom AssertJ/JUnit interop extension point
    // -------------------------------------------------------------------------

    @Test
    public void givenProcessor_whenAssertingPayloadAs_thenCustomAssertionsRun() {
        PipeliteTestFixture.given(Preconditions.inputPayload(21))
            .when(Actions.process((ioContext, c) -> ioContext.setOutputPayload(ioContext.getInputPayloadAs(Integer.class) * 2)))
            .then(Expectations.payloadAs(Integer.class, value -> Assert.assertEquals(Integer.valueOf(42), value)));
    }

    // -------------------------------------------------------------------------
    // Guard clause: when(...) must be called before inspection methods
    // -------------------------------------------------------------------------

    @Test(expected = IllegalStateException.class)
    public void whenGetOutputPayloadCalledBeforeProcess_thenThrowsIllegalStateException() {
        ((ThenOperations) PipeliteTestFixture.given(Preconditions.inputPayload("data"))).getOutputPayload();
    }

    @Test(expected = IllegalStateException.class)
    public void whenGetHeaderAsCalledBeforeProcess_thenThrowsIllegalStateException() {
        ((ThenOperations) PipeliteTestFixture.given()).getHeaderAs("X-Anything", String.class);
    }
}
