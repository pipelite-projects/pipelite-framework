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

import static io.pipelite.test.PipeliteTest.*;
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
        given(inputPayload("hello"))
            .when(process((ioContext, c) -> ioContext.setOutputPayload("world")))
            .then(isSuccess());
    }

    @Test
    public void givenProcessor_whenThrowsRuntimeException_thenContributionIsFailure() {
        RuntimeException expected = new RuntimeException("boom");

        given(inputPayload("input"))
            .when(process((ioContext, c) -> {
                throw expected;
            }))
            .then(isFailure(), failureCause(expected));
    }

    @Test
    public void givenProcessor_whenCallsStopExecution_thenIsExecutionStoppedAndStillSuccess() {
        given(inputPayload("filter-me"))
            .when(process((ioContext, c) -> c.stopExecution()))
            .then(isExecutionStopped(), isSuccess());
    }

    // -------------------------------------------------------------------------
    // Output payload
    // -------------------------------------------------------------------------

    @Test
    public void givenProcessor_whenSetsOutputPayload_thenGetOutputPayloadReturnsIt() {
        given(inputPayload(100))
            .when(process((ioContext, c) -> ioContext.setOutputPayload(200)))
            .then(payloadEquals(200));
    }

    @Test
    public void givenProcessor_whenDoesNotSetOutput_thenInputPayloadIsForwarded() {
        given(inputPayload("original"))
            .when(process((ioContext, c) -> { /* no output set */ }))
            .then(payloadEquals("original"));
    }

    @Test
    public void givenProcessor_whenSetsMapOutput_thenGetOutputPayloadAsReturnsTypedMap() {
        Map<String, Object> transformed = new HashMap<>();
        transformed.put("price", 122.0);

        ThenOperations then = given(inputPayload(Map.of("price", 100)))
            .when(process((ioContext, c) -> ioContext.setOutputPayload(transformed)))
            .then(payloadEquals(transformed));

        then.getOutputPayloadAs(Map.class);
    }

    @Test(expected = ClassCastException.class)
    public void givenProcessor_whenGetOutputPayloadAsWrongType_thenThrowsClassCastException() {
        ThenOperations then = given(inputPayload("text"))
            .when(process((ioContext, c) -> ioContext.setOutputPayload("result")));

        then.getOutputPayloadAs(Integer.class);
    }

    @Test
    public void givenNoPayload_whenExecuteProcessor_thenOutputPayloadIsNull() {
        given()
            .when(process((ioContext, c) -> { /* nothing */ }))
            .then(payloadEquals(null));
    }

    // -------------------------------------------------------------------------
    // Headers
    // -------------------------------------------------------------------------

    @Test
    public void givenHeader_whenProcessorReadsIt_thenValueIsCorrect() {
        String[] capturedValue = new String[1];

        given(
                header("Source-System", "Legacy-API"),
                inputPayload("data"))
            .when(process((ioContext, c) -> capturedValue[0] = ioContext.tryGetHeader("Source-System").orElse(null)))
            .then((exchange, contribution) -> {
                if (!"Legacy-API".equals(capturedValue[0])) {
                    throw new AssertionError(String.format(
                        "Expected processor to read header value <Legacy-API> but read <%s>", capturedValue[0]));
                }
            });
    }

    @Test
    public void givenMultipleHeaders_whenExecuteProcessor_thenAllHeadersAvailableAfterExecution() {
        given(
                header("X-Tenant", "acme"),
                header("X-Correlation-Id", "abc-123"),
                inputPayload("data"))
            .when(process((ioContext, c) -> { /* nothing */ }))
            .then(
                headerEquals("X-Tenant", "acme"),
                headerEquals("X-Correlation-Id", "abc-123"));
    }

    @Test
    public void givenProcessor_whenAddsHeader_thenNewHeaderIsVisibleAfterExecution() {
        given(inputPayload("data"))
            .when(process((ioContext, c) -> ioContext.putHeader("X-Processed-By", "unit-test")))
            .then(headerEquals("X-Processed-By", "unit-test"));
    }

    @Test
    public void givenAbsentHeader_whenGetHeaderAs_thenReturnsNull() {
        given(inputPayload("data"))
            .when(process((ioContext, c) -> { /* nothing */ }))
            .then(noHeader("X-Missing"));
    }

    @Test
    public void givenIntegerHeader_whenGetHeaderAs_thenReturnsTypedValue() {
        given(
                header("Retry-Count", 3),
                inputPayload("data"))
            .when(process((ioContext, c) -> { /* nothing */ }))
            .then(headerEquals("Retry-Count", 3));
    }

    // -------------------------------------------------------------------------
    // Expectations.payloadAs — custom AssertJ/JUnit interop extension point
    // -------------------------------------------------------------------------

    @Test
    public void givenProcessor_whenAssertingPayloadAs_thenCustomAssertionsRun() {
        given(inputPayload(21))
            .when(process((ioContext, c) -> ioContext.setOutputPayload(ioContext.getInputPayloadAs(Integer.class) * 2)))
            .then(payloadAs(Integer.class, value -> Assert.assertEquals(Integer.valueOf(42), value)));
    }

    // -------------------------------------------------------------------------
    // payloadAs — null payload edge case (Finding #15)
    // -------------------------------------------------------------------------

    @Test
    public void givenNullPayload_whenPayloadAs_thenConsumerReceivesNull() {
        // Regression test for Finding #15: payloadAs(Class, Consumer) must not throw
        // when both input and output payloads are null — the consumer should receive null.
        given()
            .when(process((ioContext, c) -> { /* no payload set */ }))
            .then(payloadAs(String.class, value -> Assert.assertNull(value)));
    }

    // -------------------------------------------------------------------------
    // Processor-mode expectation used in flow mode (Finding #12)
    // -------------------------------------------------------------------------

    @Test(expected = AssertionError.class)
    public void givenFlowMode_whenProcessorModeExpectationUsed_thenClearAssertionError() {
        // Regression test for Finding #12: isSuccess() / isFailure() / isExecutionStopped()
        // require a TestProcessContribution that does not exist in flow mode.
        // The error must be a clear AssertionError, not a NullPointerException.
        io.pipelite.dsl.definition.FlowDefinition flow = io.pipelite.core.Pipelite.defineFlow("pm-in-fm")
            .fromSource("pm-in")
            .process("step", (io, c) -> {})
            .toSink("pm-out")
            .build();

        given(flowDefinition(flow), inputPayload("x"))
            .when(supplyTo("pm-in"))
            .then(isSuccess());
    }

    // -------------------------------------------------------------------------
    // Guard clause: when(...) must be called before inspection methods
    // -------------------------------------------------------------------------

    @Test(expected = IllegalStateException.class)
    public void whenGetOutputPayloadCalledBeforeProcess_thenThrowsIllegalStateException() {
        ((ThenOperations) given(inputPayload("data"))).getOutputPayload();
    }

    @Test(expected = IllegalStateException.class)
    public void whenGetHeaderAsCalledBeforeProcess_thenThrowsIllegalStateException() {
        ((ThenOperations) given()).getHeaderAs("X-Anything", String.class);
    }
}
