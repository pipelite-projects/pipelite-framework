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

import io.pipelite.core.context.ContextValidationException;
import io.pipelite.dsl.definition.FlowDefinition;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collection;
import java.util.List;

/**
 * Issue #88: the validation phase runs independent validators over the whole context, logs
 * warnings, and fails once, with every error, so nothing is fixed one restart at a time.
 */
public class ContextValidatorChainTest {

    private static final ValidationContext EMPTY_CONTEXT = new ValidationContext() {
        @Override
        public Collection<FlowDefinition> flowDefinitions() {
            return List.of();
        }

        @Override
        public String resolveURL(String rawURL) {
            return rawURL;
        }
    };

    @Test
    public void givenNoFindings_thenTheContextIsValid() {
        final ContextValidatorChain chain = new ContextValidatorChain();
        chain.add((context, report) -> { });
        chain.validate(EMPTY_CONTEXT);
    }

    @Test
    public void givenErrorsFromSeveralValidators_thenOneExceptionListsThemAll() {
        final ContextValidator first = (context, report) -> report.error("flow-a", "toSink(...): first problem");
        final ContextValidator second = (context, report) -> {
            report.error("flow-b", "toRoute(...): second problem");
            report.error(null, "a problem about the context as a whole");
        };

        try {
            ContextValidatorChain chain = new ContextValidatorChain();
            chain.add(first);
            chain.add(second);
            chain.validate(EMPTY_CONTEXT);
            Assert.fail("expected the validation to fail");
        } catch (ContextValidationException expected) {
            Assert.assertEquals(List.of(
                "Flow 'flow-a', toSink(...): first problem",
                "Flow 'flow-b', toRoute(...): second problem",
                "a problem about the context as a whole"), expected.getProblems());
            Assert.assertEquals("Context validation failed:" +
                "\n  - Flow 'flow-a', toSink(...): first problem" +
                "\n  - Flow 'flow-b', toRoute(...): second problem" +
                "\n  - a problem about the context as a whole", expected.getMessage());
        }
    }

    @Test
    public void givenOnlyWarnings_thenTheContextIsStillValid() {
        final ContextValidatorChain chain = new ContextValidatorChain();
        chain.add((context, report) -> report.warn("flow-a", "only worth a look"));
        chain.validate(EMPTY_CONTEXT);
    }

    @Test
    public void givenAWarningAndAnError_thenOnlyTheErrorFails() {
        final ContextValidator validator = (context, report) -> {
            report.warn("flow-a", "only worth a look");
            report.error("flow-b", "must be fixed");
        };
        try {
            final ContextValidatorChain chain = new ContextValidatorChain();
            chain.add(validator);
            chain.validate(EMPTY_CONTEXT);
            Assert.fail("expected the validation to fail");
        } catch (ContextValidationException expected) {
            Assert.assertEquals(List.of("Flow 'flow-b', must be fixed"), expected.getProblems());
        }
    }

    @Test
    public void givenAValidatorAddedLater_thenItRunsAfterTheOnesAlreadyInTheChain() {
        final List<String> ran = new java.util.ArrayList<>();
        final ContextValidatorChain chain = new ContextValidatorChain();
        chain.add((context, report) -> ran.add("first"));
        chain.add((context, report) -> ran.add("second"));
        chain.add((context, report) -> ran.add("third"));

        chain.validate(EMPTY_CONTEXT);

        Assert.assertEquals(List.of("first", "second", "third"), ran);
    }

    @Test(expected = NullPointerException.class)
    public void givenNoValidator_whenAddingOne_thenItIsRejected() {
        new ContextValidatorChain().add(null);
    }

    @Test
    public void givenAnEmptyChain_thenAnyContextIsValid() {
        new ContextValidatorChain().validate(EMPTY_CONTEXT);
    }

}
