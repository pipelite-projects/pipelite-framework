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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RecipientListImpl extends ArrayList<String> implements RecipientList {

    private final Map<String, Condition> conditions;

    public RecipientListImpl(){
        conditions = new HashMap<>();
    }

    public void addRecipient(String recipient){
        if(!contains(recipient)){
            add(recipient);
        }
    }

    public void addRecipientCondition(String recipient, Condition condition){
        addRecipient(recipient);
        conditions.put(recipient, condition);
    }

    @Override
    public boolean isConditional(String recipient){
        return conditions.containsKey(recipient);
    }

    @Override
    public Condition getCondition(String recipient) {
        return conditions.get(recipient);
    }

    @Override
    public <T extends Condition> T getConditionAs(String recipient, Class<T> expectedConditionType) {
        final Condition condition = getCondition(recipient);
        if(condition != null){
            if(expectedConditionType.isAssignableFrom(condition.getClass())){
                return expectedConditionType.cast(condition);
            }else {
                throw new ClassCastException(String.format("Unable to cast from %s to %s", condition.getClass(), expectedConditionType));
            }
        }
        return null;
    }

    @Override
    public <T extends Condition> Optional<T> tryGetConditionAs(String recipient, Class<T> expectedConditionType){
        return Optional.ofNullable(getConditionAs(recipient, expectedConditionType));
    }

}
