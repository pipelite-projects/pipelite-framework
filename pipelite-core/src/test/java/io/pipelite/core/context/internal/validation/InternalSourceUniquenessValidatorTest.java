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
 * Issue #101: an internal source name is an address, {@code link://x} has to reach exactly one
 * flow. Only internal sources count: a resource shared with a source that has a protocol, or by two
 * flows on the same protocol, is legitimate. Built on real definitions from the DSL.
 */
public class InternalSourceUniquenessValidatorTest {

    private final InternalSourceUniquenessValidator subject = new InternalSourceUniquenessValidator();

    private static ValidationContext contextOf(Map<String, String> placeholders, FlowDefinition... definitions) {
        return new ValidationContext() {
            @Override
            public Collection<FlowDefinition> flowDefinitions() {
                return List.of(definitions);
            }

            @Override
            public String resolveURL(String rawURL) {
                String resolved = rawURL;
                for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
                    resolved = resolved.replace("${" + placeholder.getKey() + "}", placeholder.getValue());
                }
                return resolved;
            }
        };
    }

    private List<String> errorsFor(FlowDefinition... definitions) {
        return errorsFor(Map.of(), definitions);
    }

    private List<String> errorsFor(Map<String, String> placeholders, FlowDefinition... definitions) {
        final ValidationReport report = new ValidationReport();
        subject.validate(contextOf(placeholders, definitions), report);
        Assert.assertTrue("this validator never reports warnings", report.warnings().isEmpty());
        return report.errors().stream().map(ValidationReport.Finding::describe).toList();
    }

    private static FlowDefinition flow(String flowName, String source) {
        return Pipelite.defineFlow(flowName).fromSource(source).build();
    }

    @Test
    public void givenEveryInternalSourceNameIsUnique_thenThereIsNothingToReport() {
        Assert.assertEquals(List.of(), errorsFor(
            flow("first", "first-in"), flow("second", "second-in"), flow("third", "third-in")));
    }

    @Test
    public void givenTwoInternalFlowsWithTheSameSourceName_thenTheSecondIsReportedAndTheFirstIsNamed() {
        Assert.assertEquals(List.of(
            "Flow 'dup-second', fromSource(\"dup-src\"): source name 'dup-src' is already declared by flow 'dup-first'; link://dup-src must reach exactly one flow"),
            errorsFor(flow("dup-first", "dup-src"), flow("dup-second", "dup-src")));
    }

    @Test
    public void givenThreeInternalFlowsWithTheSameSourceName_thenEachOfTheLaterOnesNamesTheFirst() {
        Assert.assertEquals(List.of(
            "Flow 'b', fromSource(\"dup-src\"): source name 'dup-src' is already declared by flow 'a'; link://dup-src must reach exactly one flow",
            "Flow 'c', fromSource(\"dup-src\"): source name 'dup-src' is already declared by flow 'a'; link://dup-src must reach exactly one flow"),
            errorsFor(flow("a", "dup-src"), flow("b", "dup-src"), flow("c", "dup-src")));
    }

    @Test
    public void givenAnInternalSourceSharingItsResourceWithASourceThatHasAProtocol_thenItIsNotAConflict() {
        Assert.assertEquals(List.of(), errorsFor(
            flow("http-flow", "http://orders"),
            flow("internal-flow", "orders"),
            flow("kafka-flow", "kafka://orders")));
    }

    @Test
    public void givenTwoFlowsOnTheSameResourceOfAProtocol_thenItIsNotAConflict() {
        Assert.assertEquals(List.of(), errorsFor(
            flow("billing-flow", "kafka://orders"),
            flow("shipping-flow", "kafka://orders")));
    }

    @Test
    public void givenTheSameNameWithDifferentQueryParameters_thenItIsStillTheSameSource() {
        Assert.assertEquals(List.of(
            "Flow 'concurrent', fromSource(\"orders\"): source name 'orders' is already declared by flow 'plain'; link://orders must reach exactly one flow"),
            errorsFor(flow("plain", "orders"), flow("concurrent", "orders?concurrency=2")));
    }

    @Test
    public void givenAPlaceholderThatResolvesToTheSameName_thenItIsReported() {
        Assert.assertEquals(List.of(
            "Flow 'from-placeholder', fromSource(\"orders\"): source name 'orders' is already declared by flow 'literal'; link://orders must reach exactly one flow"),
            errorsFor(Map.of("inbound", "orders"), flow("literal", "orders"), flow("from-placeholder", "${inbound}")));
    }

    @Test
    public void givenASourceThatCannotBeResolved_thenItIsLeftToTheRegistrationToFailOnIt() {
        Assert.assertEquals(List.of(), errorsFor(
            flow("first", "${undefined}"), flow("second", "${undefined}")));
    }

}
