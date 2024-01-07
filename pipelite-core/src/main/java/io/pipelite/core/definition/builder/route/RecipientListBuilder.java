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
package io.pipelite.core.definition.builder.route;

import io.pipelite.common.support.Preconditions;
import io.pipelite.common.support.Builder;
import io.pipelite.dsl.definition.builder.route.RecipientListOperations;
import io.pipelite.dsl.route.ExpressionCondition;
import io.pipelite.dsl.route.RecipientList;
import io.pipelite.dsl.route.RecipientListImpl;

import java.util.Arrays;
import java.util.Collection;

public class RecipientListBuilder implements RecipientListOperations, RecipientListOperations.WhenOperations,
    RecipientListOperations.ToRecipientOperations {

    private final Builder<RecipientListImpl> builder;

    private String expression;

    public RecipientListBuilder() {
        builder = Builder.forType(RecipientListImpl.class);
    }

    @Override
    public WhenOperations when(String expression) {
        Preconditions.notNull(expression, "expression is required and cannot be null");
        Preconditions.hasText(expression, "expression must have text");
        this.expression = expression;
        return this;
    }

    @Override
    public ToRecipientOperations toRecipient(String recipient) {
        toRecipients(recipient);
        return this;
    }

    @Override
    public ToRecipientOperations toRecipients(String... recipients) {

        Preconditions.notNull(recipients, "recipients is required and cannot be null");
        final Collection<String> recipientLists = Arrays.asList(recipients);
        recipientLists.forEach(recipient -> builder.with(t -> t.addRecipient(recipient)));

        if(expression != null){
            // IMPORTANT - Copy expressionValue to prevent reset!
            final String expressionValue = expression;
            recipientLists.forEach(recipient ->
                builder.with(t -> t.addRecipientCondition(recipient, new ExpressionCondition(expressionValue))));
            expression = null;
        }
        return this;
    }
/*
    @Override
    public OtherwiseOperations otherwise(String recipient) {
        return null;
    }*/

    @Override
    public RecipientList end() {
        return builder.build();
    }
}
