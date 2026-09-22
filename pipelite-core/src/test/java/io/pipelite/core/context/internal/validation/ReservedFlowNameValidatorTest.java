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
package io.pipelite.core.context.internal.validation;

import io.pipelite.core.Pipelite;
import io.pipelite.dsl.definition.FlowDefinition;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Issue #116: {@code retry-channel} is reserved for the framework's own retry channel, on both
 * axes a collision can happen on - the flow's own name and the queue its source reads. Built on
 * real definitions from the DSL.
 */
public class ReservedFlowNameValidatorTest {

    private final ReservedFlowNameValidator subject = new ReservedFlowNameValidator();

    private static ValidationContext contextOf(FlowDefinition... definitions) {
        return new ValidationContext() {
            @Override
            public Collection<FlowDefinition> flowDefinitions() {
                return List.of(definitions);
            }

            @Override
            public String resolveURL(String rawURL) {
                return rawURL;
            }
        };
    }

    private List<String> errorsFor(FlowDefinition... definitions) {
        final ValidationReport report = new ValidationReport();
        subject.validate(contextOf(definitions), report);
        Assert.assertTrue("this validator never reports warnings", report.warnings().isEmpty());
        return report.errors().stream().map(ValidationReport.Finding::describe).toList();
    }

    @Test
    public void givenNoFlowUsesTheReservedName_thenThereIsNothingToReport() {
        Assert.assertEquals(List.of(), errorsFor(
            Pipelite.defineFlow("orders-flow").fromSource("queue://orders").build()));
    }

    @Test
    public void givenAFlowNamedRetryChannel_thenItIsRejected() {
        Assert.assertEquals(List.of(
            "Flow 'retry-channel', defineFlow(\"retry-channel\"): this name is reserved for the framework's own retry channel; choose a different flow name"),
            errorsFor(Pipelite.defineFlow("retry-channel").fromSource("queue://something-else").build()));
    }

    @Test
    public void givenAFlowReadingTheQueueRetryChannel_thenItIsRejected() {
        Assert.assertEquals(List.of(
            "Flow 'user-flow', fromSource(\"queue://retry-channel\"): 'retry-channel' is reserved for the framework's own retry channel; choose a different queue name"),
            errorsFor(Pipelite.defineFlow("user-flow").fromSource("queue://retry-channel").build()));
    }

    @Test
    public void givenAFlowThatIsBothNamedAndReadsRetryChannel_thenBothAreReported() {
        Assert.assertEquals(List.of(
            "Flow 'retry-channel', defineFlow(\"retry-channel\"): this name is reserved for the framework's own retry channel; choose a different flow name",
            "Flow 'retry-channel', fromSource(\"queue://retry-channel\"): 'retry-channel' is reserved for the framework's own retry channel; choose a different queue name"),
            errorsFor(Pipelite.defineFlow("retry-channel").fromSource("queue://retry-channel").build()));
    }

    @Test
    public void givenASourceOnAnotherProtocolNamedRetryChannel_thenItIsNotAConflict() {
        Assert.assertEquals(List.of(), errorsFor(
            Pipelite.defineFlow("kafka-flow").fromSource("kafka://retry-channel").build()));
    }

    @Test
    public void givenAPlaceholderThatResolvesToTheReservedQueueName_thenItIsReported() {
        final ValidationContext context = new ValidationContext() {
            @Override
            public Collection<FlowDefinition> flowDefinitions() {
                return List.of(Pipelite.defineFlow("from-placeholder").fromSource("${inbound}").build());
            }

            @Override
            public String resolveURL(String rawURL) {
                return rawURL.replace("${inbound}", "queue://retry-channel");
            }
        };
        final ValidationReport report = new ValidationReport();
        subject.validate(context, report);
        Assert.assertEquals(List.of(
            "Flow 'from-placeholder', fromSource(\"queue://retry-channel\"): 'retry-channel' is reserved for the framework's own retry channel; choose a different queue name"),
            report.errors().stream().map(ValidationReport.Finding::describe).toList());
    }

}
