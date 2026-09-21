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
 * Issue #88: every {@code link://x} a flow sends to must be the source endpoint name of a flow in
 * the same context. Built on real definitions from the DSL, so what is read is what a user wrote.
 */
public class FlowReferenceValidatorTest {

    private final FlowReferenceValidator subject = new FlowReferenceValidator();

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

    private static FlowDefinition receiver(String source) {
        return Pipelite.defineFlow(source + "-flow").fromSource(source).build();
    }

    @Test
    public void givenEveryConstructPointingAtARegisteredFlow_thenThereIsNothingToReport() {
        final FlowDefinition sender = Pipelite.defineFlow("sender")
            .fromSource("sender-in")
            .wireTap("audit", "link://audit-in")
            .toSink("link://sink-in")
            .withRetry(retry -> retry.maxAttempts(2).onErrorChannel(err -> err.toChannel("link://dead-letter-in")))
            .build();
        final FlowDefinition router = Pipelite.defineFlow("router")
            .fromSource("router-in")
            .toRoute(routes -> routes.dynamic()
                .when("Headers['x'] == 'y'").then("link://route-a-in")
                .otherwise("link://route-b-in")
                .end())
            .build();
        final FlowDefinition recipients = Pipelite.defineFlow("recipients")
            .fromSource("recipients-in")
            .toRecipientList(list -> list
                .toRecipients("link://recipient-a-in", "link://recipient-b-in")
                .when("Headers['x'] eq 'y'").toRecipient("slf4j://audit-logger")
                .end())
            .build();

        Assert.assertEquals(List.of(), errorsFor(sender, router, recipients,
            receiver("audit-in"), receiver("sink-in"), receiver("dead-letter-in"),
            receiver("route-a-in"), receiver("route-b-in"), receiver("recipient-a-in"), receiver("recipient-b-in")));
    }

    @Test
    public void givenAnOrphanToSink_thenItIsReportedWithTheFlowTheConstructAndTheTarget() {
        final FlowDefinition sender = Pipelite.defineFlow("order-ingress-flow")
            .fromSource("http://orders")
            .toSink("link://kicthen-start")
            .build();

        Assert.assertEquals(List.of(
            "Flow 'order-ingress-flow', toSink(...): target 'link://kicthen-start' has no registered flow declaring fromSource(\"kicthen-start\")"),
            errorsFor(sender, receiver("kitchen-start")));
    }

    @Test
    public void givenAnOrphanInEveryOtherConstruct_thenEachIsReported() {
        final FlowDefinition wireTapped = Pipelite.defineFlow("wire-tapped")
            .fromSource("wire-tapped-in")
            .wireTap("audit", "link://missing-audit")
            .build();
        final FlowDefinition routed = Pipelite.defineFlow("routed")
            .fromSource("routed-in")
            .toRoute(routes -> routes.dynamic()
                .when("Headers['x'] == 'y'").then("link://missing-then")
                .otherwise("link://missing-otherwise")
                .end())
            .build();
        final FlowDefinition recipients = Pipelite.defineFlow("recipients")
            .fromSource("recipients-in")
            .toRecipientList(list -> list.toRecipients("link://missing-recipient")
                .when("Headers['x'] eq 'y'").toRecipient("slf4j://audit-logger")
                .end())
            .build();
        final FlowDefinition deadLettering = Pipelite.defineFlow("dead-lettering")
            .fromSource("dead-lettering-in")
            .withErrorChannel(err -> err.toChannel("link://missing-error-channel"))
            .build();
        final FlowDefinition retrying = Pipelite.defineFlow("retrying")
            .fromSource("retrying-in")
            .withRetry(retry -> retry.maxAttempts(2).onErrorChannel(err -> err.toChannel("link://missing-exhaustion")))
            .build();

        final List<String> errors = errorsFor(wireTapped, routed, recipients, deadLettering, retrying);

        Assert.assertEquals(List.of(
            "Flow 'wire-tapped', wireTap(...): target 'link://missing-audit' has no registered flow declaring fromSource(\"missing-audit\")",
            "Flow 'routed', toRoute(...): target 'link://missing-then' has no registered flow declaring fromSource(\"missing-then\")",
            "Flow 'routed', toRoute(...): target 'link://missing-otherwise' has no registered flow declaring fromSource(\"missing-otherwise\")",
            "Flow 'recipients', toRecipientList(...): target 'link://missing-recipient' has no registered flow declaring fromSource(\"missing-recipient\")",
            "Flow 'dead-lettering', toChannel(...): target 'link://missing-error-channel' has no registered flow declaring fromSource(\"missing-error-channel\")",
            "Flow 'retrying', withRetry(...) onErrorChannel(toChannel(...)): target 'link://missing-exhaustion' has no registered flow declaring fromSource(\"missing-exhaustion\")"),
            errors);
    }

    @Test
    public void givenSeveralProblemsInOneFlow_thenAllAreReported() {
        final FlowDefinition sender = Pipelite.defineFlow("sender")
            .fromSource("sender-in")
            .wireTap("audit", "link://missing-one")
            .toSink("link://missing-two")
            .build();

        Assert.assertEquals(2, errorsFor(sender).size());
    }

    @Test
    public void givenADestinationThatIsNotALinkOrIsOnlyKnownAtRuntime_thenItIsNotChecked() {
        final FlowDefinition sender = Pipelite.defineFlow("sender")
            .fromSource("sender-in")
            .wireTap("audit", "slf4j://audit-logger")
            .toRoute(routes -> routes.dynamic()
                .when("Headers['x'] == 'y'").then("#{Headers['destination']}")
                .otherwise("kafka://fallback-topic")
                .end())
            .build();
        final FlowDefinition sinking = Pipelite.defineFlow("sinking")
            .fromSource("sinking-in")
            .toSink("kafka://orders")
            .withErrorChannel(err -> err.toDLQ())
            .build();

        Assert.assertEquals(List.of(), errorsFor(sender, sinking));
    }

    @Test
    public void givenASourceThatHasAProtocol_thenALinkToItsResourceStillHasNoTarget() {
        // link:// reaches only flows whose source has no protocol (see LinkChannelAdapter), so a
        // kafka:// source named "orders" is not something link://orders can deliver to.
        final FlowDefinition kafkaFed = Pipelite.defineFlow("kafka-fed").fromSource("kafka://orders").build();
        final FlowDefinition sender = Pipelite.defineFlow("sender")
            .fromSource("sender-in")
            .toSink("link://orders")
            .build();

        Assert.assertEquals(1, errorsFor(kafkaFed, sender).size());
    }

    @Test
    public void givenPlaceholdersInTheSinkAndTheSource_thenTheyAreResolvedBeforeComparing() {
        final FlowDefinition sender = Pipelite.defineFlow("sender")
            .fromSource("sender-in")
            .toSink("link://${target.name}")
            .build();
        final FlowDefinition target = Pipelite.defineFlow("target")
            .fromSource("${target.name}")
            .build();

        Assert.assertEquals(List.of(), errorsFor(Map.of("target.name", "resolved-start"), sender, target));
    }

    @Test
    public void givenASourceWithQueryParameters_thenOnlyItsResourceIsTheName() {
        final FlowDefinition target = Pipelite.defineFlow("target").fromSource("kitchen-start?concurrency=4").build();
        final FlowDefinition sender = Pipelite.defineFlow("sender")
            .fromSource("sender-in")
            .toSink("link://kitchen-start")
            .build();

        Assert.assertEquals(List.of(), errorsFor(sender, target));
    }

}
