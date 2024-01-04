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
package io.pipelite.dsl.route;

import java.util.Collection;
import java.util.Optional;

public interface RecipientList extends Collection<String> {

    static RecipientList of(String... recipients){
        final RecipientListImpl recipientList = new RecipientListImpl();
        for(String recipient : recipients){
            recipientList.addRecipient(recipient);
        }
        return recipientList;
    }

    boolean isConditional(String recipient);

    Condition getCondition(String recipient);

    <T extends Condition> T getConditionAs(String recipient, Class<T> expectedConditionType);

    <T extends Condition> Optional<T> tryGetConditionAs(String recipient, Class<T> expectedConditionType);
}
